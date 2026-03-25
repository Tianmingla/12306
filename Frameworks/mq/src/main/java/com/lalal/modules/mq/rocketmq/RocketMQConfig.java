package com.lalal.modules.mq.rocketmq;

import com.lalal.modules.mq.MessageQueueService;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置类
 */
@Configuration
@EnableConfigurationProperties(RocketMQProperties.class)
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMQConfig {

    @Autowired
    private RocketMQProperties rocketMQProperties;

    @Bean
    public MessageQueueService rocketMQMessageQueueService(RocketMQTemplate rocketMQTemplate) {
        RocketMQMessageQueueService service = new RocketMQMessageQueueService();
        return service;
    }
}
