package com.lalal.modules.config;


import com.lalal.modules.memory.PersistentChatMemoryRepository;
import lombok.Data;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class MemoryConfig {
    @Autowired
    private PersistentChatMemoryRepository repository;
    @Value("${agent.memory.max-messages}")
    private Integer maxMessages;
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
