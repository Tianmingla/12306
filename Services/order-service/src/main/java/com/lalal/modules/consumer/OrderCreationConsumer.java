package com.lalal.modules.consumer;

import com.lalal.modules.dto.OrderCreationRequestMessage;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.OrderService;
import com.lalal.modules.service.ReminderService;
import com.lalal.modules.service.WaitlistService;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单创建消费者
 * 监听 order-creation-topic
 * 调用 OrderService 创建订单
 * 发送 OrderCreationResultMessage 到 order-creation-result-topic 回调
 * 发送 对应延迟消息 travel-reminder-topic 做出行服务提醒
 * 发送 延迟消息 到 order-timeout-cancel-topic 订单超时取消
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "order-creation-topic",
    tag = "create",
    consumerGroup = "order-creation-consumer"
)
@RocketMQMessageListener(
    topic = "order-creation-topic",
    consumerGroup = "order-creation-consumer",
    selectorExpression = "create"
)
public class OrderCreationConsumer extends RocketMQBaseConsumer {

    private final OrderService orderService;
    private final WaitlistService waitlistService;
    private final WaitlistQueueService waitlistQueueService;
    private final MessageQueueService messageQueueService;
    private final ReminderService reminderService;

    private static final String ORDER_CREATION_RESULT_TOPIC = "order-creation-result-topic";
    private static final String ORDER_TIMEOUT_CANCEL_TOPIC = "order-timeout-cancel-topic";

    @Override
    protected void doProcess(Object msg) {
        OrderCreationRequestMessage message = (OrderCreationRequestMessage) msg;
        String requestId = message.getRequestId();

        log.info("[订单创建] 收到消息: requestId={}, trainNum={}, waitlistSn={}",
                requestId, message.getTrainNum(), message.getWaitlistSn());

        try {
            // 转换为 OrderCreateRequestDTO
            OrderCreateRequestDTO createRequest = convertToCreateRequest(message);

            // 调用订单服务创建订单
            String orderSn = orderService.createOrder(createRequest);

            // 计算总金额
            BigDecimal totalAmount = message.getItems().stream()
                .map(OrderCreationRequestMessage.OrderItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 发送成功结果
            OrderCreationResultMessage resultMsg = new OrderCreationResultMessage();
            resultMsg.setRequestId(requestId);
            resultMsg.setSuccess(true);
            resultMsg.setOrderSn(orderSn);
            resultMsg.setTotalAmount(totalAmount);
            resultMsg.setTimestamp(System.currentTimeMillis());

            // 候补订单：更新状态为已兑现
            if (message.getWaitlistSn() != null && !message.getWaitlistSn().isBlank()) {
                waitlistService.updateWaitlistStatus(message.getWaitlistSn(), 2, orderSn);
                // 从队列移除
                waitlistQueueService.remove(message.getWaitlistSn(),
                        message.getTrainNum(), message.getRunDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                log.info("[订单创建] 候补订单已兑现: waitlistSn={}, orderSn={}",
                        message.getWaitlistSn(), orderSn);
            }

            log.info("[订单创建] 订单创建成功: requestId={}, orderSn={}", requestId, orderSn);

            //处理成功之后 发生的事件
            messageQueueService.send(ORDER_CREATION_RESULT_TOPIC, "result", resultMsg);
            // 初始化出行提醒（延迟消息 + 版本控制）
            try {
                initReminder(orderSn, message);
            } catch (Exception e) {
                log.warn("[订单创建] 提醒初始化失败，不影响订单: orderSn={}", orderSn, e);
            }
            //发送超时取消延迟消息
            messageQueueService.sendDelay(ORDER_TIMEOUT_CANCEL_TOPIC,resultMsg,30*60*1000);
        } catch (Exception e) {
            log.error("[订单创建] 订单创建失败: requestId={}", requestId, e);

            // 发送失败结果
            OrderCreationResultMessage resultMsg = new OrderCreationResultMessage();
            resultMsg.setRequestId(requestId);
            resultMsg.setSuccess(false);
            resultMsg.setErrorMessage("订单创建失败: " + e.getMessage());
            resultMsg.setTimestamp(System.currentTimeMillis());

            messageQueueService.send(ORDER_CREATION_RESULT_TOPIC, "result", resultMsg);

            throw e; // 触发 MQ 重试
        }
    }

    /**
     * 将 OrderCreationRequestMessage 转换为 OrderCreateRequestDTO
     */
    private OrderCreateRequestDTO convertToCreateRequest(OrderCreationRequestMessage msg) {
        OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
        dto.setTrainNumber(msg.getTrainNum());
        dto.setStartStation(msg.getStartStation());
        dto.setEndStation(msg.getEndStation());
        dto.setUsername(msg.getUsername());
        dto.setRunDate(msg.getRunDate());

        // 转换订单项
        if (msg.getItems() != null) {
            dto.setItems(msg.getItems().stream()
                .map(item -> {
                    OrderCreateRequestDTO.OrderItemRequestDTO itemDto = new OrderCreateRequestDTO.OrderItemRequestDTO();
                    itemDto.setPassengerId(item.getPassengerId());
                    itemDto.setCarriageNumber(item.getCarriageNumber());
                    itemDto.setSeatNumber(item.getSeatNumber());
                    itemDto.setSeatType(item.getSeatType());
                    itemDto.setAmount(item.getAmount());
                    itemDto.setRealName(item.getRealName());
                    itemDto.setIdCard(item.getIdCard());
                    return itemDto;
                })
                .collect(Collectors.toList()));
        }

        return dto;
    }

    /**
     * 初始化出行提醒
     * 发送延迟消息（发车前1h、30m、到达提醒）
     */
    private void initReminder(String orderSn, OrderCreationRequestMessage message) {
        // 获取车次时刻信息（需要从 ticket-service 获取或从消息中携带）
        // 这里简化处理，假设消息中有时间信息
        // 实际应该查询 t_train_station 表获取发车/到达时间

        String trainNum = message.getTrainNum();
        String runDate = message.getRunDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String startStation = message.getStartStation();
        String endStation = message.getEndStation();
        String username = message.getUsername();

        // 获取第一个乘客姓名
        String passengerName = message.getItems() != null && !message.getItems().isEmpty()
                ? message.getItems().get(0).getRealName()
                : "";

        LocalDate date = message.getRunDate();
        long planDepartTime = date.atTime(8, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long planArrivalTime = date.atTime(12, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        reminderService.initReminderState(
                orderSn, trainNum, runDate,
                startStation, endStation,
                username, passengerName,
                planDepartTime, planArrivalTime
        );

        log.info("[订单创建] 提醒初始化完成: orderSn={}, trainNum={}", orderSn, trainNum);
    }
}
