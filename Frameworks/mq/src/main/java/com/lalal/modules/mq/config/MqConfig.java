package com.lalal.modules.mq.config;


import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.TestMqService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {
    @Bean
    MessageQueueService messageQueueService(){
        //TODO
        return new TestMqService();
    }
}
