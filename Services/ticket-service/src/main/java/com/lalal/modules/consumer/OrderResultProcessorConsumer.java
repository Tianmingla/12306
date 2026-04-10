package com.lalal.modules.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.SeatReleaseMessage;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 订单创建结果处理消费者
 * 监听 order-creation-result-topic
 * 订单成功：更新缓存状态为成功，记录订单号
 * 订单失败：发送座位释放消息，更新缓存状态为失败
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "order-creation-result-topic",
    tag = "*",
    consumerGroup = "order-result-processor-consumer"
)
@RocketMQMessageListener(
    topic = "order-creation-result-topic",
    consumerGroup = "order-result-processor-consumer",
    selectorExpression = "*"
)
public class OrderResultProcessorConsumer extends RocketMQBaseConsumer {

    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;

    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";
    private static final String SEAT_RELEASE_TOPIC = "seat-release-topic";

    @Override
    protected void doProcess(Object msg) {
        OrderCreationResultMessage orderResult = (OrderCreationResultMessage) msg;
        String requestId = orderResult.getRequestId();

        log.info("[订单结果处理] 收到消息: requestId={}, success={}, orderSn={}",
            requestId, orderResult.isSuccess(), orderResult.getOrderSn());

        // 查询异步请求记录
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;
        TicketAsyncRequestDO record = safeCacheTemplate.get(asyncKey, new TypeReference<TicketAsyncRequestDO>() {});

        if (record == null) {
            log.warn("[订单结果处理] 未找到请求记录: requestId={}", requestId);
            return;
        }

//        // 幂等性检查：如果状态已经是最终状态，跳过
//        if (record.getStatus() == 1 || record.getStatus() == 2) {
//            log.info("[订单结果处理] 请求已处于最终状态: requestId={}, status={}", requestId, record.getStatus());
//            return;
//        }

        if (orderResult.isSuccess()) {
            // 订单创建成功
            record.setStatus(1); // SUCCESS
            record.setOrderSn(orderResult.getOrderSn());
            safeCacheTemplate.set(asyncKey,record,30, TimeUnit.MINUTES);

            log.info("[订单结果处理] 订单创建成功: requestId={}, orderSn={}, totalAmount={}",
                requestId, orderResult.getOrderSn(), orderResult.getTotalAmount());

        } else {
            // 订单创建失败，需要释放座位
            record.setStatus(2); // FAILED
            record.setErrorMessage(orderResult.getErrorMessage());
            safeCacheTemplate.set(asyncKey,record,30,TimeUnit.MINUTES);

            // 发送座位释放消息
            sendSeatReleaseMessage(requestId, record);

            log.info("[订单结果处理] 订单创建失败，已发送座位释放消息: requestId={}, error={}",
                requestId, orderResult.getErrorMessage());
        }
    }

    /**
     * 发送座位释放消息
     */
    private void sendSeatReleaseMessage(String requestId, TicketAsyncRequestDO record) {
        SeatReleaseMessage releaseMsg = new SeatReleaseMessage();
        releaseMsg.setRequestId(requestId);
        releaseMsg.setTrainNum(record.getTrainNum());
        releaseMsg.setDate(record.getDate());
        releaseMsg.setReason("订单创建失败");
        releaseMsg.setTimestamp(System.currentTimeMillis());

        messageQueueService.send(SEAT_RELEASE_TOPIC, "release", releaseMsg);

        log.info("[订单结果处理] 已发送座位释放消息: requestId={}", requestId);
    }
}
