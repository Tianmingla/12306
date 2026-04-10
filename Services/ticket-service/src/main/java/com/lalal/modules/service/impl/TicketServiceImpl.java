package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.context.RequestContext;
import com.lalal.modules.dto.AsyncTicketPurchaseMessage;
import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.dto.SeatSelectionRequestDTO;
import com.lalal.modules.dto.TicketDTO;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.remote.OrderServiceClient;
import com.lalal.modules.remote.SeatServiceClient;
import com.lalal.modules.remote.UserServiceClient;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.TicketService;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.utils.RequestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final SeatServiceClient seatServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final SafeCacheTemplate safeCacheTemplate;
    private final FareCalculationService fareCalculationService;
    private final TrainMapper trainMapper;
    private final TicketAsyncRequestMapper ticketAsyncRequestMapper;
    private final MessageQueueService messageQueueService;

    private static final String PEAK_STATUS_KEY = "traffic:peak:status";
    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";

    @Override
    public PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto, Long userId) {
        // 基础校验
        if (userId == null) {
            return PurchaseTicketVO.failed("用户ID为空");
        }
        if (purchaseTicketRequestDto.getIDCardCodelist() == null
                || purchaseTicketRequestDto.getSeatTypelist() == null
                || purchaseTicketRequestDto.getIDCardCodelist().size() != purchaseTicketRequestDto.getSeatTypelist().size()) {
            return PurchaseTicketVO.failed("乘车人ID列表与座位类型列表不匹配");
        }

        // 查询乘客信息
        UserServiceClient.PassengersBatchRequest batchReq = new UserServiceClient.PassengersBatchRequest();
        batchReq.setUserId(userId);
        batchReq.setPassengerIds(purchaseTicketRequestDto.getIDCardCodelist());
        Result<List<UserServiceClient.PassengerRemoteVO>> passengerResult = userServiceClient.batchPassengers(batchReq);
        if (passengerResult == null
                || passengerResult.getCode() == null
                || !ReturnCode.success.code().equals(passengerResult.getCode())
                || passengerResult.getData() == null
                || passengerResult.getData().isEmpty()) {
            return PurchaseTicketVO.failed("获取乘车人信息失败");
        }
        List<UserServiceClient.PassengerRemoteVO> passengers = passengerResult.getData();

        // 检测是否为高峰时段
        String peakStatus = safeCacheTemplate.get(PEAK_STATUS_KEY, new TypeReference<String>() {});
        boolean peakHour = "true".equals(peakStatus);

        if (peakHour) {
            // 高峰模式：异步处理
            return handlePeakPurchase(purchaseTicketRequestDto, userId, passengers);
        }

        // 低峰模式：同步处理
        return processCorePurchase(userId, purchaseTicketRequestDto.getTrainNum(),
                purchaseTicketRequestDto.getStartStation(), purchaseTicketRequestDto.getEndStation(),
                purchaseTicketRequestDto.getDate(), purchaseTicketRequestDto.getIDCardCodelist(),
                purchaseTicketRequestDto.getSeatTypelist(), purchaseTicketRequestDto.getChooseSeats(),
                purchaseTicketRequestDto.getAccount(), passengers);
    }

    /**
     * 高峰模式处理：返回 PROCESSING 状态 + requestId
     * 不插入数据库，仅用 Redis 存储临时状态
     */
    private PurchaseTicketVO handlePeakPurchase(PurchaseTicketRequestDto request, Long userId,
                                                 List<UserServiceClient.PassengerRemoteVO> passengers) {
        String requestId = RequestContext.getRequestId();
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;

        // 缓存存储初始状态对象（status=0 表示处理中）
        TicketAsyncRequestDO initialRecord = TicketAsyncRequestDO.builder()
                .requestId(requestId)
                .userId(userId)
                .trainNum(request.getTrainNum())
                .date(java.sql.Date.valueOf(request.getDate()))
                .status(0)
                .build();

        boolean set = safeCacheTemplate.setIfAbsent(asyncKey, initialRecord, 30, TimeUnit.MINUTES);
        if (!set) {
            return PurchaseTicketVO.failed("请求处理中，请稍后查询");
        }

        // 构建MQ消息
        AsyncTicketPurchaseMessage message = new AsyncTicketPurchaseMessage();
        message.setRequestId(requestId);
        message.setUserId(userId);
        message.setAccount(StringUtils.hasText(request.getAccount()) ? request.getAccount() : "");
        message.setTrainNum(request.getTrainNum());
        message.setStartStation(request.getStartStation());
        message.setEndStation(request.getEndStation());
        message.setDate(request.getDate());
        message.setPassengerIds(request.getIDCardCodelist());
        message.setSeatTypelist(request.getSeatTypelist());
        message.setChooseSeats(request.getChooseSeats());
        message.setTimestamp(System.currentTimeMillis());

        // 异步发送消息
        try {
            messageQueueService.send("ticket-purchase-topic", "purchase", message);
            log.info("[高峰模式] 发送异步购票消息: requestId={}, trainNum={}", requestId, request.getTrainNum());
        } catch (Exception e) {
            // 发送失败，清理 Redis 状态
            safeCacheTemplate.del(asyncKey);
            log.error("[高峰模式] 发送消息失败: requestId={}", requestId, e);
            return PurchaseTicketVO.failed("消息发送失败，请重试");
        }

        return PurchaseTicketVO.processing(requestId);
    }

    /**
     * 核心购票逻辑（同步路径 + 异步路径复用）
     */
    @Override
    public PurchaseTicketVO processCorePurchase(Long userId, String trainNum, String startStation,
                                                 String endStation, String date, List<Long> passengerIds,
                                                 List<String> seatTypelist, List<String> chooseSeats,
                                                 String account) {
        return processCorePurchase(userId, trainNum, startStation, endStation, date,
                passengerIds, seatTypelist, chooseSeats, account, null);
    }

    /**
     * 核心购票逻辑（内部重载，支持传入乘客列表）
     */
    private PurchaseTicketVO processCorePurchase(Long userId, String trainNum, String startStation,
                                                  String endStation, String date, List<Long> passengerIds,
                                                  List<String> seatTypelist, List<String> chooseSeats,
                                                  String account,
                                                  List<UserServiceClient.PassengerRemoteVO> passengers) {
        // 如果传入了passengers，直接使用；否则查询
        if (passengers == null) {
            UserServiceClient.PassengersBatchRequest batchReq = new UserServiceClient.PassengersBatchRequest();
            batchReq.setUserId(userId);
            batchReq.setPassengerIds(passengerIds);
            Result<List<UserServiceClient.PassengerRemoteVO>> passengerResult = userServiceClient.batchPassengers(batchReq);
            if (passengerResult == null
                    || passengerResult.getCode() == null
                    || !ReturnCode.success.code().equals(passengerResult.getCode())
                    || passengerResult.getData() == null
                    || passengerResult.getData().isEmpty()) {
                return PurchaseTicketVO.failed("获取乘车人信息失败");
            }
            passengers = passengerResult.getData();
        }

        // 1. 选座
        SeatSelectionRequestDTO seatRequest = new SeatSelectionRequestDTO();
        seatRequest.setTrainNum(trainNum);
        seatRequest.setStartStation(startStation);
        seatRequest.setEndStation(endStation);
        seatRequest.setDate(LocalDate.parse(date));
        seatRequest.setAccount(StringUtils.hasText(account) ? account : "");

        List<SeatSelectionRequestDTO.PassengerDTO> seatPassengers = new ArrayList<>();
        for (int i = 0; i < passengers.size(); i++) {
            SeatSelectionRequestDTO.PassengerDTO p = new SeatSelectionRequestDTO.PassengerDTO();
            p.setId(passengers.get(i).getId());
            p.setSeatType(seatTypelist.get(i));
            if (chooseSeats != null && i < chooseSeats.size()) {
                p.setSeatPreference(chooseSeats.get(i));
            }
            seatPassengers.add(p);
        }
        seatRequest.setPassengers(seatPassengers);

        TicketDTO selectedSeats = seatServiceClient.select(seatRequest);
        if (selectedSeats == null || selectedSeats.getItems() == null) {
            return PurchaseTicketVO.failed("座位选择失败");
        }

        // 2. 获取列车信息
        TrainDO trainDO = trainMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrainDO>()
                        .eq(TrainDO::getTrainNumber, trainNum)
        );

        // 3. 计算票价
        List<FareCalculationRequestDTO> fareRequests = new ArrayList<>();
        for (TicketDTO.TicketItem item : selectedSeats.getItems()) {
            FareCalculationRequestDTO fareRequest = new FareCalculationRequestDTO();
            fareRequest.setTrainNumber(trainNum);
            fareRequest.setDepartureStation(startStation);
            fareRequest.setArrivalStation(endStation);
            fareRequest.setSeatType(item.getSeatType());
            fareRequest.setPassengerType(0);
            fareRequest.setTrainBrand(trainDO != null ? trainDO.getTrainBrand() : null);
            fareRequest.setIsPeakSeason(false);
            fareRequest.setPassengerId(item.getPassengerId());
            if (trainDO != null) {
                fareRequest.setTrainId(trainDO.getId());
            }
            fareRequests.add(fareRequest);
        }

        List<FareCalculationResultDTO> fareResults = fareCalculationService.batchCalculateFare(fareRequests);

        // 4. 创建订单
        OrderServiceClient.OrderCreateRemoteRequestDTO orderRequest = new OrderServiceClient.OrderCreateRemoteRequestDTO();
        orderRequest.setTrainNumber(trainNum);
        orderRequest.setStartStation(startStation);
        orderRequest.setEndStation(endStation);
        orderRequest.setUsername(account);
        orderRequest.setRunDate(Date.from(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant()));

        List<OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO> orderItems = new ArrayList<>();
        for (int i = 0; i < selectedSeats.getItems().size(); i++) {
            TicketDTO.TicketItem item = selectedSeats.getItems().get(i);
            FareCalculationResultDTO fareResult = fareResults.get(i);

            OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO orderItem =
                    new OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO();
            orderItem.setPassengerId(item.getPassengerId());
            orderItem.setCarriageNumber(item.getCarriageNum());
            orderItem.setSeatNumber(item.getSeatNum());
            orderItem.setSeatType(item.getSeatType());
            orderItem.setAmount(fareResult.getTotalFare());

            UserServiceClient.PassengerRemoteVO matched = passengers.stream()
                    .filter(pv -> pv.getId().equals(item.getPassengerId()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                orderItem.setIdCard(matched.getIdCardNumber());
                orderItem.setRealName(matched.getRealName());
            } else {
                orderItem.setIdCard("");
                orderItem.setRealName("");
            }
            orderItems.add(orderItem);
        }
        orderRequest.setItems(orderItems);

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String orderSn = orderServiceClient.create(orderRequest);

        // 组装返回结果
        PurchaseTicketVO vo = new PurchaseTicketVO();
        vo.setStatus(RequestStatus.SUCCESS.toString());
        vo.setOrderSn(orderSn);
        vo.setTotalAmount(totalAmount);
        vo.setTicketDTO(selectedSeats);
        return vo;
    }

    @Override
    public AsyncTicketCheckVO check(String requestId, Long userId) {
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;

        // 完全依赖缓存，不查数据库
        TicketAsyncRequestDO record = safeCacheTemplate.get(asyncKey, new TypeReference<TicketAsyncRequestDO>() {});

        if (record == null) {
            // 缓存过期或请求不存在
            return AsyncTicketCheckVO.failed(requestId, "请求不存在或已过期");
        }

        // 安全检查：确保用户只能查询自己的请求
        if (!record.getUserId().equals(userId)) {
            return AsyncTicketCheckVO.failed(requestId, "无权查询此请求");
        }

        switch (record.getStatus()) {
            case 0:
                return AsyncTicketCheckVO.processing(requestId);
            case 1:
                return AsyncTicketCheckVO.success(record.getRequestId(), record.getOrderSn(),
                        BigDecimal.ZERO, null);
            case 2:
                return AsyncTicketCheckVO.failed(requestId, record.getErrorMessage());
            case 3:
                return AsyncTicketCheckVO.failed(requestId, "消息发送失败: " + record.getErrorMessage());
            default:
                return AsyncTicketCheckVO.failed(requestId, "未知状态");
        }
    }
}
