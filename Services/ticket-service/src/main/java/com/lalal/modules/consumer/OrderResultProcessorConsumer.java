package com.lalal.modules.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.SeatReleaseMessage;
import com.lalal.modules.dto.WaitlistFulfillResultMessage;
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
 *
 * <p>处理逻辑：
 * 1. 普通购票：订单成功 → 更新缓存；订单失败 → 发送座位释放消息
 * 2. 候补订单：订单成功/失败 → 发送 WaitlistFulfillResultMessage 到 waitlist-fulfillment-result-topic
 *
 * @author Claude
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
    private static final String WAITLIST_FULFILLMENT_RESULT_TOPIC = "waitlist-fulfillment-result-topic";

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

        // 判断来源：普通购票 vs 候补订单
        String source = record.getSource() != null ? record.getSource() : "NORMAL";

        if ("WAITLIST".equals(source)) {
            // ==================== 候补订单处理 ====================
            handleWaitlistFulfillment(record, orderResult);
        } else {
            // ==================== 普通购票处理 ====================
            handleNormalPurchase(orderResult, record);
        }
    }

    /**
     * 处理普通购票结果
     */
    private void handleNormalPurchase(OrderCreationResultMessage orderResult, TicketAsyncRequestDO record) {
        String requestId = record.getRequestId();
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;

        if (orderResult.isSuccess()) {
            // 订单创建成功
            record.setStatus(1); // SUCCESS
            record.setOrderSn(orderResult.getOrderSn());
            safeCacheTemplate.set(asyncKey,record,30, java.util.concurrent.TimeUnit.MINUTES);

            log.info("[订单结果] 普通购票成功: requestId={}, orderSn={}",
                    requestId, orderResult.getOrderSn());

        } else {
            // 订单创建失败，需要释放座位
            record.setStatus(2); // FAILED
            record.setErrorMessage(orderResult.getErrorMessage());
            safeCacheTemplate.set(asyncKey,record, 30, java.util.concurrent.TimeUnit.MINUTES);

            // 发送座位释放消息
            sendSeatReleaseMessage(requestId, record);

            log.warn("[订单结果] 普通购票失败，已释放座位: requestId={}, error={}",
                    requestId, orderResult.getErrorMessage());
        }
    }

    /**
     * 处理候补订单兑现结果
     *
     * <p>注意：此方法已废弃。候补订单的最终结果由 WaitlistResultConsumer
     * 直接监听 order-creation-result-topic 处理，不再转发。
     */
    @Deprecated
    private void handleWaitlistFulfillment(TicketAsyncRequestDO record, OrderCreationResultMessage orderResult) {
        // 候补订单由 WaitlistResultConsumer 直接处理，无需转发
        log.debug("[候补结果] 由 WaitlistResultConsumer 直接处理，跳过转发: requestId={}",
                record.getRequestId());
    }

    /**
     * 推断失败原因
     */
    private Integer inferFailureReason(String errorMessage) {
        if (errorMessage == null) {
            return 5; // 超时/未知
        }

        String msg = errorMessage.toLowerCase();

        if (msg.contains("座位") || msg.contains("seat") || msg.contains("lock")) {
            return 3; // 座位冲突
        } else if (msg.contains("支付") || msg.contains("pay") || msg.contains("余额")) {
            return 2; // 支付失败
        } else if (msg.contains("订单") && msg.contains("创建")) {
            return 4; // 订单创建失败
        } else if (msg.contains("余票") || msg.contains("sold out") || msg.contains("无票")) {
            return 1; // 无票
        } else {
            return 5; // 其他/超时
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

        log.info("[订单结果] 已发送座位释放消息: requestId={}", requestId);
    }
}

