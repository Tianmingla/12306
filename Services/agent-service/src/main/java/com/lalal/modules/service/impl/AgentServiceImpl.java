package com.lalal.modules.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.config.AgentProperties;
import com.lalal.modules.dto.ChatRequest;
import com.lalal.modules.dto.ChatResponse;
import com.lalal.modules.service.AgentService;
import com.lalal.modules.service.ModelRouterService;
import com.lalal.modules.sse.SseEmitterHelper;
import com.lalal.modules.sse.SseEmitterHelper.SseEmitterWithKeepAlive;
import com.lalal.modules.tool.ToolContextHelper;
import com.lalal.modules.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI Agent 智能客服服务实现
 * 已完成：
 * - Step 2: 多模型路由(简单→Ollama, 复杂→SSNAI) + 基础对话
 * - Step 3: ChatMemory记忆持久化(MySQL+Redis+Kryo) + Advisor链
 * - Step 4: @Tool工具调用 + ToolContext身份传递
 * - Step 5: RAG 知识库 + PGvector
 * - Step 6: SSE 真流式输出(ChatClient.stream) + 工具调用事件 + 心跳保活
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ChatClient complexChatClient;
    private final ModelRouterService modelRouterService;
    private final AgentProperties agentProperties;
    private final ChatMemory chatMemory;
    private final ToolRegistry toolRegistry;
    private final SseEmitterHelper sseEmitterHelper;

    @Override
    public SseEmitter streamChat(ChatRequest request, String userId) {
        AgentProperties.SseConfig sseConfig = agentProperties.getSse();

        // 创建带心跳保活的 SseEmitter
        SseEmitterWithKeepAlive emitterHolder = sseConfig.isSendKeepAlive()
                ? sseEmitterHelper.createEmitterWithKeepAlive(sseConfig.getTimeout(), sseConfig.getKeepAliveInterval())
                : new SseEmitterWithKeepAlive(sseEmitterHelper.createEmitter(sseConfig.getTimeout()), null);
        SseEmitter emitter = emitterHolder.emitter();

        String conversationId = resolveConversationId(request);
        String userMessage = request.getMessage();

        // 模型路由
        ChatClient chatClient = modelRouterService.route(userMessage);
        boolean isComplex = modelRouterService.isComplex(userMessage);
        String modelName = isComplex
                ? agentProperties.getModel().getComplex()
                : agentProperties.getModel().getSimple();

        // 发送 start 事件
        sseEmitterHelper.sendStart(emitter, conversationId, modelName);

        // 构建记忆 Advisor
        MessageChatMemoryAdvisor memoryAdvisor = (MessageChatMemoryAdvisor) MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .build();

        // 构建工具上下文
        Map<String, Object> toolContextMap = ToolContextHelper.buildToolContext(userId, null).getContext();
        ToolCallback[] toolCallbacks = isComplex ? toolRegistry.getAllToolCallbacks() : new ToolCallback[0];

        // 用于跟踪工具调用状态
        Set<String> emittedToolCalls = new HashSet<>();
        AtomicBoolean inToolExecution = new AtomicBoolean(false);

        // 使用 ChatClient.stream() 实现真流式输出
        // chatResponse() 返回 Flux<ChatResponse>，可以检测 tool calls
        // 如果流式不支持（如某些 Ollama 模型），降级到同步模式
        Disposable subscription;
        try {
            Flux<ChatResponse> responseFlux = chatClient.prompt()
                    .user(userMessage)
                    .advisors(memoryAdvisor)
                    .toolContext(toolContextMap)
                    .tools(toolCallbacks)
                    .stream()
                    .chatResponse();

            // 订阅 Flux，将每个事件转发到 SseEmitter
            subscription = responseFlux.subscribe(
                    chatResponse -> {
                        try {
                            handleStreamChunk(emitter, chatResponse, conversationId, emittedToolCalls, inToolExecution);
                        } catch (Exception e) {
                            log.error("Error handling stream chunk", e);
                        }
                    },
                    error -> {
                        log.error("Stream error for conversation {}: {}", conversationId, error.getMessage(), error);
                        sseEmitterHelper.sendError(emitter, "处理请求时发生错误，请稍后重试", conversationId);
                        sseEmitterHelper.sendDone(emitter, conversationId);
                        sseEmitterHelper.safeComplete(emitter);
                        emitterHolder.cancelKeepAlive();
                    },
                    () -> {
                        log.info("Stream completed for conversation: {}", conversationId);
                        sseEmitterHelper.sendDone(emitter, conversationId);
                        sseEmitterHelper.safeComplete(emitter);
                        emitterHolder.cancelKeepAlive();
                    }
            );
        } catch (UnsupportedOperationException e) {
            // Ollama 某些模型不支持流式，降级到同步模式
            log.warn("Streaming not supported, falling back to sync mode for conversation: {}", conversationId);
            subscription = Flux.<String>empty().subscribe();
            CompletableFuture.runAsync(() -> {
                try {
                    String response = chatClient.prompt()
                            .user(userMessage)
                            .advisors(memoryAdvisor)
                            .toolContext(toolContextMap)
                            .tools(toolCallbacks)
                            .call()
                            .content();
                    if (response != null) {
                        sseEmitterHelper.sendToken(emitter, response, conversationId);
                    }
                    sseEmitterHelper.sendDone(emitter, conversationId);
                    sseEmitterHelper.safeComplete(emitter);
                } catch (Exception ex) {
                    sseEmitterHelper.sendError(emitter, "处理请求时发生错误", conversationId);
                    sseEmitterHelper.safeCompleteWithError(emitter, ex);
                } finally {
                    emitterHolder.cancelKeepAlive();
                }
            });
        }

        // 连接关闭时取消订阅
        emitter.onCompletion(() -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        emitter.onTimeout(() -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        emitter.onError(ex -> {
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });

        return emitter;
    }

    /**
     * 处理流式响应的每个 chunk
     * 检测文本 token 和工具调用，发送对应的 SSE 事件
     */
    private void handleStreamChunk(SseEmitter emitter,
                                   ChatResponse chatResponse,
                                   String conversationId,
                                   Set<String> emittedToolCalls,
                                   AtomicBoolean inToolExecution) {
        if (chatResponse == null) return;

        // 1. 检测工具调用
        if (chatResponse.hasToolCalls()) {
            List<org.springframework.ai.chat.messages.ToolCall> toolCalls =
                    chatResponse.getResult().getOutput().getToolCalls();

            for (var toolCall : toolCalls) {
                String toolCallId = toolCall.id();
                // 避免重复发送同一个 tool call（流式可能多次触发）
                if (emittedToolCalls.add(toolCallId)) {
                    String toolName = toolCall.name();
                    String arguments = toolCall.arguments();

                    log.info("Tool call detected: {} with args: {}", toolName,
                            arguments.length() > 200 ? arguments.substring(0, 200) + "..." : arguments);

                    // 发送 tool_call 事件
                    sseEmitterHelper.sendToolCall(emitter, toolName, arguments, conversationId);

                    // 发送 thinking 事件表示正在执行工具
                    String thinkingContent = getToolThinkingMessage(toolName);
                    sseEmitterHelper.sendThinking(emitter, thinkingContent, conversationId);

                    inToolExecution.set(true);
                }
            }
            return; // tool call chunk 不包含文本，直接返回
        }

        // 2. 提取文本 token
        String token = chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null
                ? chatResponse.getResult().getOutput().getContent()
                : null;

        if (token != null && !token.isEmpty()) {
            // 如果之前在工具执行中，现在有文本输出了，说明工具执行完毕
            if (inToolExecution.compareAndSet(true, false)) {
                // 可以在这里发送 tool_result 事件
                // 但由于 ToolCallingAdvisor 自动处理，我们无法精确知道哪个工具的结果
                // 简化方案：不单独发 tool_result，直接发文本 token
                log.debug("Tool execution completed, resuming text output");
            }

            // 发送文本 token
            sseEmitterHelper.sendToken(emitter, token, conversationId);
        }
    }

    /**
     * 根据工具名生成思考提示
     */
    private String getToolThinkingMessage(String toolName) {
        if (toolName == null) return "正在处理...";
        return switch (toolName) {
            case "searchDirectTrains" -> "正在搜索直达车次...";
            case "searchTransferTrains" -> "正在搜索换乘方案...";
            case "getTrainStationDetails" -> "正在查询经停站信息...";
            case "queryOrderDetail" -> "正在查询订单详情...";
            case "queryMyOrders" -> "正在查询您的订单列表...";
            case "refundTicket" -> "正在处理退票...";
            case "cancelOrder" -> "正在取消订单...";
            case "queryWaitlistOrders" -> "正在查询候补订单...";
            case "queryMyPassengers" -> "正在查询乘车人信息...";
            default -> "正在调用工具 " + toolName + "...";
        };
    }

    @Override
    public ChatResponse chat(ChatRequest request, String userId) {
        String conversationId = resolveConversationId(request);

        // 模型路由：简单问答→Ollama，复杂推理→SSNAI
        ChatClient chatClient = modelRouterService.route(request.getMessage());
        boolean isComplex = modelRouterService.isComplex(request.getMessage());

        // 调用ChatClient：
        // 1. memoryAdvisor — 多轮对话记忆
        // 2. conversationId — 会话隔离
        // 3. toolContext — 传递用户身份给@Tool方法
        MessageChatMemoryAdvisor memoryAdvisor = (MessageChatMemoryAdvisor) MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .build();

        ToolCallback[] toolCallbacks = isComplex ? toolRegistry.getAllToolCallbacks() : new ToolCallback[0];

        String response = chatClient.prompt()
                .user(request.getMessage())
                .advisors(memoryAdvisor)
                .toolContext(ToolContextHelper.buildToolContext(userId, null).getContext())
                .tools(toolCallbacks)
                .call()
                .content();

        ChatResponse chatResponse = ChatResponse.text(response, conversationId);
        log.info("Chat completed: conversationId={}, model={}, isComplex={}, userId={}",
                conversationId,
                isComplex ? agentProperties.getModel().getComplex() : agentProperties.getModel().getSimple(),
                isComplex, userId);
        return chatResponse;
    }

    @Override
    public ChatResponse confirmAction(String confirmId, boolean approved, String userId) {
        // TODO: Step 7 - Human-in-the-Loop 实现
        if (approved) {
            return ChatResponse.text("操作已确认执行", null);
        } else {
            return ChatResponse.text("操作已取消", null);
        }
    }

    private String resolveConversationId(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        return conversationId;
    }
}
