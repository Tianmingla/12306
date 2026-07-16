package com.lalal.modules.memory;

import com.lalal.modules.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置
 * 将自定义 PersistentChatMemoryRepository 注册为 Spring AI 的 ChatMemory 组件
 *
 * 注意：MessageChatMemoryAdvisor 在 AiModelConfig 中注册为 ChatClient 的 defaultAdvisor，
 * conversationId 在调用时通过 advisors param 传入（Spring AI 2.0.0 API）
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatMemoryConfig {

    private final AgentProperties agentProperties;

    /**
     * ChatMemory 实例
     * 使用 MessageWindowChatMemory 包装 Repository，限制单次对话最大消息数
     * 超过 maxMessages 的旧消息会被自动裁剪
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        int maxMessages = agentProperties.getMemory().getMaxMessages();
        log.info("Initializing ChatMemory with maxMessages={}, repository={}",
                maxMessages, chatMemoryRepository.getClass().getSimpleName());

        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }
}
