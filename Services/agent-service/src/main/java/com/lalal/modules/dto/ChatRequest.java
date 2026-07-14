package com.lalal.modules.dto;

import lombok.Data;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequest {
    /** 用户消息内容 */
    private String message;
    /** 会话ID（用于多轮对话记忆） */
    private String conversationId;
    /** 是否使用流式输出 */
    private boolean stream = true;
}
