package com.lalal.modules.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.WaitlistFulfillResultMessage;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.WaitlistService;
import com.lalal.modules.service.WaitlistQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 候补兑现结果消费者
 * 监听 order-creation-result-topic，处理候补订单的最终结果：
 * 1. 成功：更新候补状态为"已兑现"，记录订单号，从队列移除
 * 2. 失败：更新候补状态回"待兑现"，触发优先级惩罚，重新排队
 *
 * @author Claude
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "order-creation-result-topic",
        tag = "*",
        consumerGroup = "waitlist-result-consumer"
)
@RocketMQMessageListener(
        topic = "order-creation-result-topic",
        consumerGroup = "waitlist-result-consumer",
        selectorExpression = "*"
)
public class WaitlistResultConsumer extends RocketMQBaseConsumer {

    private final WaitlistService waitlistService;
    private final WaitlistQueueService waitlistQueueService;
    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;

    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";
    private static final String WAITLIST_FULFILLMENT_RESULT_TOPIC = "waitlist-fulfillment-result-topic";

    @Override
    protected void doProcess(Object msg) {
        OrderCreationResultMessage orderResult=(OrderCreationResultMessage)msg;
        String requestId = orderResult.getRequestId();

        log.info("[候补结果] 处理: requestId={}, success={}, orderSn={}",
                requestId, orderResult.isSuccess(), orderResult.getOrderSn());

        try {
            // 1. 查询异步请求记录，获取候补订单号
            String asyncKey = ASYNC_REQUEST_PREFIX + requestId;
            TicketAsyncRequestDO record = safeCacheTemplate.get(asyncKey,
                    new TypeReference<TicketAsyncRequestDO>() {});

            if (record == null || record.getWaitlistSn() == null) {
                log.warn("[候补结果] 非候补订单或记录不存在，跳过: requestId={}", requestId);
                return;
            }

            String waitlistSn = record.getWaitlistSn();

            // 2. 查询候补订单
            var order = waitlistService.findByWaitlistSn(waitlistSn);
            if (order == null) {
                log.error("[候补结果] 候补订单不存在: waitlistSn={}", waitlistSn);
                return;
            }

            // 3. 状态检查（幂等性保护）
            if (order.getStatus() == 2 || order.getStatus() == 3 || order.getStatus() == 4) {
                log.warn("[候补结果] 订单状态已终态，跳过: waitlistSn={}, status={}",
                        waitlistSn, order.getStatus());
                return;
            }

            // 4. 根据结果处理
            if (orderResult.isSuccess()) {
                // --- 候补购票成功 ---
                order.setStatus(2); // 已兑现
                order.setFulfilledOrderSn(orderResult.getOrderSn());
                waitlistService.updateById(order);

                // 从候补队列移除
                waitlistQueueService.remove(
                        waitlistSn,
                        order.getTrainNumber(),
                        order.getTravelDate().toString()
                );

                log.info("[候补结果] 兑现成功: waitlistSn={}, orderSn={}",
                        waitlistSn, orderResult.getOrderSn());

            } else {
                // --- 候补购票失败 ---
                order.setStatus(0); // 回"待兑现"，继续排队
                waitlistService.updateById(order);

                // 触发优先级惩罚
                waitlistService.recalculatePriorityWithPenalty(order);

                log.warn("[候补结果] 购票失败，继续排队: waitlistSn={}, error={}",
                        waitlistSn, orderResult.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[候补结果] 处理异常: requestId={}", requestId, e);
            throw e;
        }
    }
}
