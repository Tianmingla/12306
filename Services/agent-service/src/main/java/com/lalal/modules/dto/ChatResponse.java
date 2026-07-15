package com.lalal.modules.dto;

import lombok.Data;

/**
 * 聊天响应 DTO
 */
@Data
public class ChatResponse {
    /** 消息内容 */
    private String content;
    /** 会话ID */
    private String conversationId;
    /** 消息类型：text / tool_call / tool_result / thinking / confirm */
    private String type;
    /** 工具名称（type=tool_call时） */
    private String toolName;
    /** 是否需要人工确认 */
    private boolean needConfirm;
    /** 确认操作ID */
    private String confirmId;

    public static ChatResponse text(String content, String conversationId) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setConversationId(conversationId);
        resp.setType("text");
        return resp;
    }

    public static ChatResponse thinking(String content, String conversationId) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setConversationId(conversationId);
        resp.setType("thinking");
        return resp;
    }

    public static ChatResponse toolCall(String toolName, String content, String conversationId) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setConversationId(conversationId);
        resp.setType("tool_call");
        resp.setToolName(toolName);
        return resp;
    }

    public static ChatResponse toolResult(String toolName, String result, String conversationId) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(result);
        resp.setConversationId(conversationId);
        resp.setType("tool_result");
        resp.setToolName(toolName);
        return resp;
    }

    public static ChatResponse confirm(String content, String confirmId, String conversationId) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setConversationId(conversationId);
        resp.setType("confirm");
        resp.setNeedConfirm(true);
        resp.setConfirmId(confirmId);
        return resp;
    }
}
