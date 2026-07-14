package com.lalal.modules.service;

import com.lalal.modules.dto.ChatRequest;
import com.lalal.modules.dto.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Agent 智能客服服务接口
 */
public interface AgentService {

    /**
     * SSE 流式对话
     * @param request 聊天请求
     * @param userId 用户ID（从JWT解析）
     * @return SseEmitter 流式发射器
     */
    SseEmitter streamChat(ChatRequest request, String userId);

    /**
     * 同步对话
     * @param request 聊天请求
     * @param userId 用户ID
     * @return 聊天响应
     */
    ChatResponse chat(ChatRequest request, String userId);

    /**
     * 人工确认操作
     * @param confirmId 确认操作ID
     * @param approved 是否批准
     * @param userId 用户ID
     * @return 执行结果
     */
    ChatResponse confirmAction(String confirmId, boolean approved, String userId);
}
