package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.AsyncTicketPurchaseMessage;
import com.lalal.modules.dto.TicketDTO;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.service.AsyncTicketPurchaseService;
import com.lalal.modules.service.TicketService;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.remote.OrderServiceClient;
import com.lalal.modules.remote.SeatServiceClient;
import com.lalal.modules.remote.UserServiceClient;
import com.lalal.modules.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 异步购票服务实现
 */
@Service
@RequiredArgsConstructor
public class AsyncTicketPurchaseServiceImpl extends ServiceImpl<TicketAsyncRequestMapper, TicketAsyncRequestDO>
        implements AsyncTicketPurchaseService {

    private final TicketService ticketService;
    private final SeatServiceClient seatServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final FareCalculationService fareCalculationService;
    private final MessageQueueService messageQueueService;

    private static final String ASYNC_REQUEST_PROCESSING_KEY = "ticket:async:processing:";

    @Override
    public String createAsyncRequest(AsyncTicketPurchaseMessage message) {
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 幂等性检查：如果已处理过，直接返回
        String processingKey = ASYNC_REQUEST_PROCESSING_KEY + requestId;
        Boolean added = redisTemplate.opsForValue().setIfAbsent(processingKey, "1", 30 * 60, TimeUnit.SECONDS);
        if (added == null || !added) {
            // 已存在，可能是重复提交，返回已存在的requestId
            return requestId;
        }

        // 构建请求参数JSON
        String requestParams = buildRequestParamsJson(message);

        // 保存异步请求记录
        TicketAsyncRequestDO record = TicketAsyncRequestDO.builder()
                .requestId(requestId)
                .userId(message.getUserId())
                .trainNum(message.getTrainNum())
                .date(String.valueOf(new Date(message.getTimestamp())))
                .status(0)
                .requestParams(requestParams)
                .build();
        save(record);

        // 发送MQ消息
        try {
            messageQueueService.send("ticket-purchase-topic", "purchase", message);
        } catch (Exception e) {
            // 发送失败，更新状态
            record.setStatus(3);
            record.setErrorMessage("消息发送失败: " + e.getMessage());
            updateById(record);
            throw e;
        }

        return requestId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processAsyncPurchase(AsyncTicketPurchaseMessage message) {
        String requestId = message.getRequestId();

        // 幂等性检查：已处理过的请求直接跳过
        TicketAsyncRequestDO existing = lambdaQuery()
                .eq(TicketAsyncRequestDO::getRequestId, requestId)
                .one();
        if (existing != null && existing.getStatus() == 1) {
            // 已成功，跳过
            return;
        }

        // 再次验证请求参数
        if (message.getPassengerIds() == null || message.getPassengerIds().isEmpty()) {
            updateStatus(requestId, 2, "乘车人列表为空");
            return;
        }

        try {
            // 重建 PurchaseTicketRequestDto 用于同步调用
            PurchaseTicketRequestDto request = new PurchaseTicketRequestDto();
            request.setAccount(message.getAccount());
            request.setIDCardCodelist(message.getPassengerIds());
            request.setSeatTypelist(message.getSeatTypelist());
            request.setChooseSeats(message.getChooseSeats());
            request.setTrainNum(message.getTrainNum());
            request.setStartStation(message.getStartStation());
            request.setEndStation(message.getEndStation());
            request.setDate(message.getDate());

            // 调用核心购票逻辑（复用同步流程）
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

            if (result == null || result.getOrderSn() == null) {
                updateStatus(requestId, 2, "购票处理失败");
                return;
            }

            // 更新为成功
            updateStatus(requestId, 1, null);
            setOrderSn(requestId, result.getOrderSn());

        } catch (Exception e) {
            // 记录失败原因
            updateStatus(requestId, 2, "购票异常: " + e.getMessage());
            throw e; // 触发MQ重试
        }
    }

    @Override
    public AsyncTicketCheckVO checkStatus(String requestId, Long userId) {
        TicketAsyncRequestDO record = lambdaQuery()
                .eq(TicketAsyncRequestDO::getRequestId, requestId)
                .eq(TicketAsyncRequestDO::getUserId, userId)
                .one();

        if (record == null) {
            return AsyncTicketCheckVO.failed(requestId, "请求不存在");
        }

        switch (record.getStatus()) {
            case 0: // 处理中
                return AsyncTicketCheckVO.processing(requestId);
            case 1: // 成功
                // TODO: 需要查询订单详情返回完整票务信息
                return AsyncTicketCheckVO.success(record.getRequestId(), record.getOrderSn(),
                        BigDecimal.ZERO, null);
            case 2: // 失败
                return AsyncTicketCheckVO.failed(record.getRequestId(), record.getErrorMessage());
            case 3: // 发送失败
                return AsyncTicketCheckVO.failed(record.getRequestId(),
                        "消息发送失败，请重试: " + record.getErrorMessage());
            default:
                return AsyncTicketCheckVO.failed(record.getRequestId(), "未知状态");
        }
    }

    /**
     * 更新状态
     */
    private void updateStatus(String requestId, Integer status, String errorMessage) {
        TicketAsyncRequestDO update = new TicketAsyncRequestDO();
        update.setRequestId(requestId);
        update.setStatus(status);
        if (errorMessage != null) {
            update.setErrorMessage(errorMessage);
        }
        updateById(update);
    }

    /**
     * 设置订单号
     */
    private void setOrderSn(String requestId, String orderSn) {
        TicketAsyncRequestDO update = new TicketAsyncRequestDO();
        update.setRequestId(requestId);
        update.setOrderSn(orderSn);
        updateById(update);
    }

    /**
     * 构建请求参数JSON（简化版，实际可用 Jackson 序列化）
     */
    private String buildRequestParamsJson(AsyncTicketPurchaseMessage message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"trainNum\":\"").append(message.getTrainNum())
                .append("\",\"userId\":").append(message.getUserId())
                .append(",\"passengerIds\":").append(message.getPassengerIds())
                .append(",\"date\":\"").append(message.getDate()).append("\"}");
        return sb.toString();
    }
}
