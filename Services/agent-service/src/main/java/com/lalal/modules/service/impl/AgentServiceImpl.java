package com.lalal.modules.service.impl;

import com.lalal.modules.agent.AgentExecutor;
import com.lalal.modules.config.AgentProperties;
import com.lalal.modules.dto.ChatRequest;
import com.lalal.modules.dto.ChatResponse;
import com.lalal.modules.entity.AgentPendingActionDO;
import com.lalal.modules.service.AgentService;
import com.lalal.modules.service.ModelRouterService;
import com.lalal.modules.sse.SseEmitterHelper;
import com.lalal.modules.sse.SseEmitterHelper.SseEmitterWithKeepAlive;
import com.lalal.modules.tool.ToolContextHelper;
import com.lalal.modules.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
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
 * - Step 7: 分层智能体(死循环检测 + Human-in-the-Loop + 步骤限制) + AgentExecutor
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
    private final AgentExecutor agentExecutor;

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

        // 构建工具上下文
        Map<String, Object> toolContextMap = ToolContextHelper.buildToolContext(userId, null).getContext();
        ToolCallbackProvider toolCallbackProvider = isComplex ? toolRegistry.getToolCallbackProvider() : null;

        // 用于跟踪工具调用状态
        Set<String> emittedToolCalls = new HashSet<>();
        AtomicBoolean inToolExecution = new AtomicBoolean(false);

        // Step 7: 为此会话创建/获取死循环检测器
        // 每次新请求复用同一个检测器（多轮对话中累积检测）
        agentExecutor.getOrCreateLoopDetector(conversationId);

        // 使用 ChatClient.stream() 实现真流式输出
        Disposable subscription;
        try {
            Flux<org.springframework.ai.chat.model.ChatResponse> responseFlux = chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .toolContext(toolContextMap)
                    .toolCallbacks(toolCallbackProvider)
                    .stream()
                    .chatResponse();

            // 订阅 Flux，将每个事件转发到 SseEmitter
            subscription = responseFlux.subscribe(
                    chatResponse -> {
                        try {
                            handleStreamChunk(emitter, chatResponse, conversationId,
                                    emittedToolCalls, inToolExecution);
                        } catch (Exception e) {
                            log.error("Error handling stream chunk", e);
                        }
                    },
                    error -> {
                        log.error("Stream error for conversation {}: {}", conversationId, error.getMessage(), error);
                        // Step 7: 检查是否是死循环导致的错误
                        if (agentExecutor.isLoopDetected(conversationId)) {
                            String reason = agentExecutor.getLoopReason(conversationId);
                            sseEmitterHelper.sendError(emitter,
                                    "Agent 执行异常：" + reason + "，请重新描述您的需求", conversationId);
                        } else {
                            sseEmitterHelper.sendError(emitter, "处理请求时发生错误，请稍后重试", conversationId);
                        }
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
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .toolContext(toolContextMap)
                            .toolCallbacks(toolCallbackProvider)
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

        // 连接关闭时取消订阅 + 清理执行状态
        Disposable finalSubscription = subscription;
        emitter.onCompletion(() -> {
            if (!finalSubscription.isDisposed()) {
                finalSubscription.dispose();
            }
            agentExecutor.cleanup(conversationId);
        });
        emitter.onTimeout(() -> {
            if (!finalSubscription.isDisposed()) {
                finalSubscription.dispose();
            }
            agentExecutor.cleanup(conversationId);
        });
        emitter.onError(ex -> {
            if (!finalSubscription.isDisposed()) {
                finalSubscription.dispose();
            }
            agentExecutor.cleanup(conversationId);
        });

        return emitter;
    }

    /**
     * 处理流式响应的每个 chunk
     * 检测文本 token 和工具调用，发送对应的 SSE 事件
     *
     * Step 7 增强：加入死循环检测
     */
    private void handleStreamChunk(SseEmitter emitter,
                                   org.springframework.ai.chat.model.ChatResponse chatResponse,
                                   String conversationId,
                                   Set<String> emittedToolCalls,
                                   AtomicBoolean inToolExecution) {
        if (chatResponse == null) return;

        // Step 7: 死循环检测 — 如果已检测到死循环，发送错误并终止
        if (agentExecutor.isLoopDetected(conversationId)) {
            String reason = agentExecutor.getLoopReason(conversationId);
            sseEmitterHelper.sendError(emitter, "Agent 执行异常：" + reason, conversationId);
            sseEmitterHelper.sendDone(emitter, conversationId);
            sseEmitterHelper.safeComplete(emitter);
            return;
        }

        // 1. 检测工具调用
        if (chatResponse.hasToolCalls()) {
            List<org.springframework.ai.chat.messages.AssistantMessage.ToolCall> toolCalls =
                    chatResponse.getResult().getOutput().getToolCalls();

            for (var toolCall : toolCalls) {
                String toolCallId = toolCall.id();
                if (emittedToolCalls.add(toolCallId)) {
                    String toolName = toolCall.name();
                    String arguments = toolCall.arguments();

                    // Step 7: 记录工具调用到死循环检测器
                    boolean shouldContinue = agentExecutor.recordToolCall(conversationId, toolName);
                    if (!shouldContinue) {
                        String reason = agentExecutor.getLoopReason(conversationId);
                        sseEmitterHelper.sendError(emitter, "Agent 执行异常：" + reason, conversationId);
                        sseEmitterHelper.sendDone(emitter, conversationId);
                        sseEmitterHelper.safeComplete(emitter);
                        return;
                    }

                    log.info("Tool call detected: {} (step {}/{}) with args: {}", toolName,
                            agentExecutor.getOrCreateLoopDetector(conversationId).getTotalSteps(),
                            agentProperties.getAgent().getMaxSteps(),
                            arguments.length() > 200 ? arguments.substring(0, 200) + "..." : arguments);

                    // 发送 tool_call 事件
                    sseEmitterHelper.sendToolCall(emitter, toolName, arguments, conversationId);

                    // 发送 thinking 事件表示正在执行工具
                    String thinkingContent = getToolThinkingMessage(toolName);
                    sseEmitterHelper.sendThinking(emitter, thinkingContent, conversationId);

                    inToolExecution.set(true);
                }
            }
            return;
        }

        // 2. 提取文本 token
        String token = chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null
                ? chatResponse.getResult().getOutput().getText()
                : null;

        if (token != null && !token.isEmpty()) {
            if (inToolExecution.compareAndSet(true, false)) {
                log.debug("Tool execution completed, resuming text output");
            }
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

        ChatClient chatClient = modelRouterService.route(request.getMessage());
        boolean isComplex = modelRouterService.isComplex(request.getMessage());

        ToolCallbackProvider toolCallbackProvider = isComplex ? toolRegistry.getToolCallbackProvider() : null;

        String response = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(ToolContextHelper.buildToolContext(userId, null).getContext())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .content();

        ChatResponse chatResponse = ChatResponse.text(response, conversationId);
        log.info("Chat completed: conversationId={}, model={}, isComplex={}, userId={}",
                conversationId,
                isComplex ? agentProperties.getModel().getComplex() : agentProperties.getModel().getSimple(),
                isComplex, userId);
        return chatResponse;
    }

    /**
     * 人工确认操作 — Step 7 真实实现
     *
     * 工作流程：
     * 1. 用户确认 → 从数据库取出待确认操作 → 返回操作参数供调用方执行
     * 2. 用户拒绝 → 更新状态为 rejected → 返回取消提示
     * 3. 无效确认ID → 返回错误提示
     */
    @Override
    public ChatResponse confirmAction(String confirmId, boolean approved, String userId) {
        log.info("confirmAction: confirmId={}, approved={}, userId={}", confirmId, approved, userId);

        AgentPendingActionDO action = agentExecutor.processConfirmation(confirmId, approved, userId);

        if (action == null) {
            return ChatResponse.text("确认操作无效或已过期，请重新发起操作", null);
        }

        if (approved) {
            String actionDesc = agentExecutor.getPendingActionService().getActionDescription(action.getActionType());
            return ChatResponse.confirm(
                    String.format("已确认%s操作，正在执行...", actionDesc),
                    confirmId,
                    action.getConversationId()
            );
        } else {
            String actionDesc = agentExecutor.getPendingActionService().getActionDescription(action.getActionType());
            return ChatResponse.text(
                    String.format("已取消%s操作", actionDesc),
                    action.getConversationId()
            );
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
