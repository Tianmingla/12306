package com.lalal.modules.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.dto.AsyncTicketPurchaseMessage;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.concurrent.TimeUnit;

/**
 * 异步购票消息消费者
 * 监听 ticket-purchase-topic，处理高峰时段的购票请求
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "ticket-purchase-topic",
    tag = "purchase",
    consumerGroup = "ticket-purchase-consumer"
)
@RocketMQMessageListener(
    topic = "ticket-purchase-topic",
    consumerGroup = "ticket-purchase-consumer",
    selectorExpression = "purchase"
)
public class TicketPurchaseConsumer extends RocketMQBaseConsumer {

    private final TicketService ticketService;
    private final SafeCacheTemplate safeCacheTemplate;

    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";

    @Override
    protected void doProcess(Object msg) {
        AsyncTicketPurchaseMessage message = (AsyncTicketPurchaseMessage) msg;
        String requestId = message.getRequestId();
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;

        log.info("[异步购票] 收到消息: requestId={}, trainNum={}", requestId, message.getTrainNum());

//        // 1. 幂等性检查：缓存中无数据或状态不是 PROCESSING 说明已处理过
//        TicketAsyncRequestDO existing = safeCacheTemplate.get(asyncKey, new TypeReference<TicketAsyncRequestDO>() {});
//        if (existing != null && existing.getStatus() != 0) {
//            log.info("[异步购票] 请求已处理，跳过: requestId={}, status={}", requestId, existing.getStatus());
//            return;
//        }

        // 2. 参数校验
        if (message.getPassengerIds() == null || message.getPassengerIds().isEmpty()) {
            saveToCache(asyncKey, message, 2, null, "乘车人列表为空");
            return;
        }

        try {
            // 3. 调用核心购票逻辑
            PurchaseTicketVO result = ticketService.processCorePurchase(
                    message.getUserId(),
                    message.getTrainNum(),
                    message.getStartStation(),
                    message.getEndStation(),
                    message.getDate(),
                    message.getPassengerIds(),
                    message.getSeatTypelist(),
                    message.getChooseSeats(),
                    message.getAccount()
            );

            // 4. 处理结果
            if (result == null || result.getOrderSn() == null) {
                saveToCache(asyncKey, message, 2, null, "购票处理失败: 订单创建失败");
                return;
            }

            // 5. 保存成功结果到缓存
            saveToCache(asyncKey, message, 1, result.getOrderSn(), null);

            log.info("[异步购票] 处理成功: requestId={}, orderSn={}", requestId, result.getOrderSn());

        } catch (Exception e) {
            log.error("[异步购票] 处理异常: requestId={}", requestId, e);
            saveToCache(asyncKey, message, 2, null, "购票异常: " + e.getMessage());
            throw e; // 抛出异常触发 MQ 重试
        }
    }

    /**
     * 保存处理结果到缓存
     */
    private void saveToCache(String asyncKey, AsyncTicketPurchaseMessage message,
                             int status, String orderSn, String errorMessage) {
        TicketAsyncRequestDO record = TicketAsyncRequestDO.builder()
                .requestId(message.getRequestId())
                .userId(message.getUserId())
                .trainNum(message.getTrainNum())
                .date(message.getDate())
                .status(status)
                .orderSn(orderSn)
                .errorMessage(errorMessage)
                .build();
        safeCacheTemplate.set(asyncKey, record, 1, TimeUnit.DAYS);
    }
}
