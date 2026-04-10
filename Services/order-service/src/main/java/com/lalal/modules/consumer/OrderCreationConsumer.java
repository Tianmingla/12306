package com.lalal.modules.consumer;

import com.lalal.modules.dto.OrderCreationRequestMessage;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.OrderService;
import com.lalal.modules.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 订单创建消费者
 * 监听 order-creation-topic
 * 调用 OrderService 创建订单
 * 发送 OrderCreationResultMessage 到 order-creation-result-topic
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
    private final MessageQueueService messageQueueService;

    private static final String ORDER_CREATION_RESULT_TOPIC = "order-creation-result-topic";

    @Override
    protected void doProcess(Object msg) {
        OrderCreationRequestMessage message = (OrderCreationRequestMessage) msg;
        String requestId = message.getRequestId();

        log.info("[订单创建] 收到消息: requestId={}, trainNum={}", requestId, message.getTrainNum());

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

            messageQueueService.send(ORDER_CREATION_RESULT_TOPIC, "result", resultMsg);

            log.info("[订单创建] 订单创建成功: requestId={}, orderSn={}", requestId, orderSn);

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
        dto.setRunDate(LocalDate.parse(msg.getRunDate()));

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
}
