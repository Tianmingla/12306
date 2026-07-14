package com.lalal.modules.service.impl;

import com.lalal.modules.config.AgentProperties;
import com.lalal.modules.service.ModelRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 模型路由服务实现
 * 基于关键词规则判断问题复杂度，路由到对应模型：
 * - 简单问答（规则/政策/常识）→ Ollama qwen2.5:3b 本地模型，降本60%
 * - 复杂推理（查询/购票/退改签/工具调用）→ SSNAI OpenAI兼容API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRouterServiceImpl implements ModelRouterService {

    private final ChatClient complexChatClient;

    @Qualifier("simpleChatClient")
    private final ChatClient simpleChatClient;

    private final AgentProperties agentProperties;

    @Override
    public ChatClient route(String userMessage) {
        boolean complex = isComplex(userMessage);
        ChatClient selected = complex ? complexChatClient : simpleChatClient;
        log.info("Model routing: message='{}...', complex={}, model={}",
                userMessage.substring(0, Math.min(userMessage.length(), 20)),
                complex,
                complex ? agentProperties.getModel().getComplex() : agentProperties.getModel().getSimple());
        return selected;
    }

    @Override
    public boolean isComplex(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String message = userMessage.toLowerCase();

        // 1. 检查是否包含复杂问题关键词（需要工具调用/实时数据）
        for (String keyword : agentProperties.getComplexity().getComplexKeywords()) {
            if (message.contains(keyword)) {
                return true;
            }
        }

        // 2. 检查是否包含城市名+日期模式（车次搜索意图）
        // 简单启发式：消息中同时包含"到"和数字（可能是日期）
        if (message.contains("到") && message.matches(".*\\d{1,2}[号日].*")) {
            return true;
        }

        // 3. 检查是否包含订单号模式
        if (message.matches(".*[A-Za-z0-9]{10,}.*")) {
            return true;
        }

        // 4. 默认：如果消息较长（>50字），可能是复杂问题
        if (userMessage.length() > 50) {
            return true;
        }

        // 5. 纯简单问答关键词
        for (String keyword : agentProperties.getComplexity().getSimpleKeywords()) {
            if (message.contains(keyword)) {
                return false;
            }
        }

        // 默认走复杂模型，宁可多花一点成本也不要答不好
        return true;
    }
}
