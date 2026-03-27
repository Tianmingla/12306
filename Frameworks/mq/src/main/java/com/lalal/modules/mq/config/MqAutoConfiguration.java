package com.lalal.modules.mq.config;

import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.rocketmq.RocketMQMessageQueueService;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;

/**
 * MQ 模块自动配置类
 * 自动配置 MessageQueueService 和注册消费者
 */
@Configuration
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
public class MqAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqAutoConfiguration.class);

    /**
     * 注册 RocketMQ 消息队列服务
     */
    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public MessageQueueService messageQueueService(RocketMQTemplate rocketMQTemplate) {
        log.info("Initializing RocketMQMessageQueueService");
        return new RocketMQMessageQueueService(rocketMQTemplate);
    }

    /**
     * 消费者注册处理器
     * 扫描标注了 @MessageConsumer 注解的类，并验证其正确性
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public ConsumerRegisterProcessor consumerRegisterProcessor() {
        return new ConsumerRegisterProcessor();
    }

    /**
     * 消费者注册处理器
     */
    public static class ConsumerRegisterProcessor {

        private final Logger log = LoggerFactory.getLogger(ConsumerRegisterProcessor.class);

        /**
         * 验证消费者类是否正确实现
         * 此方法会在 Spring 容器初始化时被调用
         */
        public void validateConsumer(Class<?> beanClass, com.lalal.modules.mq.annotation.MessageConsumer annotation) {
            // 验证是否继承了 BaseMessageConsumer
            if (!com.lalal.modules.mq.consumer.BaseMessageConsumer.class.isAssignableFrom(beanClass)) {
                log.warn("消费者类 [{}] 没有继承 BaseMessageConsumer，可能无法正常工作。" +
                        "建议继承 BaseMessageConsumer 并实现 doProcess 方法", beanClass.getName());
            }

            // 验证必要的配置
            if (annotation.topic().isEmpty()) {
                throw new IllegalArgumentException(
                        "消费者 [" + beanClass.getName() + "] 必须指定 topic");
            }
            if (annotation.consumerGroup().isEmpty()) {
                throw new IllegalArgumentException(
                        "消费者 [" + beanClass.getName() + "] 必须指定 consumerGroup");
            }

            log.info("已注册消费者: topic={}, tag={}, consumerGroup={}, class={}",
                    annotation.topic(), annotation.tag(),
                    annotation.consumerGroup(), beanClass.getSimpleName());
        }
    }
}
