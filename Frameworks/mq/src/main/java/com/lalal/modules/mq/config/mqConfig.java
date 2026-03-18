package com.lalal.modules.mq.config;


import com.lalal.modules.mq.MessageQueueService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class mqConfig {
    @Bean
    MessageQueueService messageQueueService(){
        //TODO
        return new MessageQueueService() {
            @Override
            public void send(String topic, Object message) {

            }

            @Override
            public void sendDelay(String topic, Object message, long delayTime) {

            }
        };
    }
}
