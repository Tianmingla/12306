package com.lalal.modules.consumer;

import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消
 * 处理订单掉未支付的订单
 * 调用取消订单方法-->求改出行提醒记录的缓存版本-->发送座位释放消息
 *                                                     -->发送waitlist-fulfill-topic候补订单从优先级队列拉取处理
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "order-timeout-cancel-topic",
        consumerGroup = "order-timeout-cancel-consumer"
)
@RocketMQMessageListener(
        topic = "order-timeout-cancel-topic",
        consumerGroup = "order-timeout-cancel-consumer",
        selectorExpression = "cancel"
)
public class OrderTimeoutCancelConsumer extends RocketMQBaseConsumer {
    private OrderService orderService;
    @Override
    protected void doProcess(Object body) {
        //复用一下这个消息类型 懒得创建新类型了
        OrderCreationResultMessage msg=(OrderCreationResultMessage) body;

        orderService.cancelOrder(msg.getOrderSn());

    }
}
