package com.lalal.modules.service;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 模型路由服务
 * 根据问题复杂度自动选择 Ollama(简单) 或 SSNAI OpenAI兼容API(复杂)
 */
public interface ModelRouterService {

    /**
     * 根据用户消息判断应该使用哪个模型
     * @param userMessage 用户消息
     * @return 对应的ChatClient
     */
    ChatClient route(String userMessage);

    /**
     * 判断问题复杂度
     * @param userMessage 用户消息
     * @return true=复杂问题(需工具调用/RAG)，false=简单问答
     */
    boolean isComplex(String userMessage);
}
