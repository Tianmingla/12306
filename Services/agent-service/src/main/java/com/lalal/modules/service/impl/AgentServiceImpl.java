package com.lalal.modules.service.impl;

import com.lalal.modules.config.AgentProperties;
import com.lalal.modules.dto.ChatRequest;
import com.lalal.modules.dto.ChatResponse;
import com.lalal.modules.service.AgentService;
import com.lalal.modules.service.ModelRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 智能客服服务实现
 * 当前完成：
 * - Step 2: 多模型路由(简单→Ollama, 复杂→SSNAI) + 基础对话
 *
 * 后续步骤逐步完善：
 * - Step 3: 记忆持久化
 * - Step 4: @Tool 工具调用
 * - Step 5: RAG 知识库
 * - Step 6: SSE 流式输出完善
 * - Step 7: 分层智能体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final ChatClient complexChatClient;
    private final ModelRouterService modelRouterService;
    private final AgentProperties agentProperties;

    @Override
    public SseEmitter streamChat(ChatRequest request, String userId) {
        long timeout = agentProperties.getSse().getTimeout();
        SseEmitter emitter = new SseEmitter(timeout);

        CompletableFuture.runAsync(() -> {
            try {
                String conversationId = resolveConversationId(request);

                // 模型路由
                ChatClient chatClient = modelRouterService.route(request.getMessage());
                boolean isComplex = modelRouterService.isComplex(request.getMessage());

                // TODO: Step 6 - 完善SSE流式输出，使用ChatClient.stream()实时推送
                String response = chatClient.prompt()
                        .user(request.getMessage())
                        .call()
                        .content();

                // 发送模型选择信息
                String modelInfo = isComplex
                        ? agentProperties.getModel().getComplex()
                        : agentProperties.getModel().getSimple();
                emitter.send(SseEmitter.event()
                        .name("model")
                        .data("{\"model\":\"" + modelInfo + "\",\"conversationId\":\"" + conversationId + "\"}"));

                // 发送文本响应
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data("{\"type\":\"text\",\"content\":\"" + escapeJson(response) + "\",\"conversationId\":\"" + conversationId + "\"}"));

                // 发送完成事件
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"conversationId\":\"" + conversationId + "\"}"));

                emitter.complete();
            } catch (IOException e) {
                log.error("SSE send error", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> log.warn("SSE connection timeout"));
        emitter.onCompletion(() -> log.debug("SSE connection completed"));
        emitter.onError(ex -> log.error("SSE connection error", ex));

        return emitter;
    }

    @Override
    public ChatResponse chat(ChatRequest request, String userId) {
        String conversationId = resolveConversationId(request);

        // 模型路由：简单问答→Ollama，复杂推理→SSNAI
        ChatClient chatClient = modelRouterService.route(request.getMessage());
        boolean isComplex = modelRouterService.isComplex(request.getMessage());

        // TODO: Step 3 - 加入记忆持久化
        // TODO: Step 4 - 加入工具调用
        // TODO: Step 5 - 加入RAG知识库检索

        String response = chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();

        ChatResponse chatResponse = ChatResponse.text(response, conversationId);
        log.info("Chat completed: conversationId={}, model={}, isComplex={}",
                conversationId,
                isComplex ? agentProperties.getModel().getComplex() : agentProperties.getModel().getSimple(),
                isComplex);
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

    /**
     * 解析会话ID，如果未提供则生成新的
     */
    private String resolveConversationId(ChatRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        return conversationId;
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
