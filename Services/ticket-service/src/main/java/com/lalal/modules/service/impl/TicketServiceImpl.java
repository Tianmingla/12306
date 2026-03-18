package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.SeatSelectionRequestDTO;
import com.lalal.modules.dto.TicketDTO;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.remote.OrderServiceClient;
import com.lalal.modules.remote.SeatServiceClient;
import com.lalal.modules.service.TicketService;
import lombok.RequiredArgsConstructor;
import com.lalal.modules.mq.MessageQueueService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService {

    private final SeatServiceClient seatServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final MessageQueueService messageQueueService;
    private final StringRedisTemplate redisTemplate;

    private static final String PEAK_STATUS_KEY = "traffic:peak:status";

    @Override
    public PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto) {
        //TODO 过滤器 请求参数验证
        PurchaseTicketVO purchaseTicketVO = new PurchaseTicketVO();

        // 检查当前流量状态
        String peakStatus = redisTemplate.opsForValue().get(PEAK_STATUS_KEY);
        boolean peakHour = "true".equals(peakStatus);

        if (peakHour) {
            // 1. 发送消息到MQ进行异步处理（削峰填谷）
            // TODO: 定义MQ Topic常量
            messageQueueService.send("ticket_purchase_topic", purchaseTicketRequestDto);

            // TODO: 需要在后台启动一个MQ消费者来处理 ticket_purchase_topic 消息，
            // 消费者逻辑应调用下方的非高峰期购票逻辑（调用座位服务和订单服务）

            purchaseTicketVO.setStatus(RequestStatus.PROCESSING.toString());
            return purchaseTicketVO;
        } else {
            // 1. 调用座位服务
            SeatSelectionRequestDTO seatRequest = new SeatSelectionRequestDTO();
            seatRequest.setTrainNum(purchaseTicketRequestDto.getTrainNum());
            seatRequest.setStartStation(purchaseTicketRequestDto.getStartStation());
            seatRequest.setEndStation(purchaseTicketRequestDto.getEndStation());
            seatRequest.setDate(LocalDate.parse(purchaseTicketRequestDto.getDate()));
            seatRequest.setAccount(purchaseTicketRequestDto.getAccount());

            List<SeatSelectionRequestDTO.PassengerDTO> passengers = new ArrayList<>();
            for (int i = 0; i < purchaseTicketRequestDto.getIDCardCodelist().size(); i++) {
                SeatSelectionRequestDTO.PassengerDTO p = new SeatSelectionRequestDTO.PassengerDTO();
                p.setId(purchaseTicketRequestDto.getIDCardCodelist().get(i));
                p.setSeatType(purchaseTicketRequestDto.getSeatTypelist().get(i));
                passengers.add(p);
            }
            seatRequest.setPassengers(passengers);

            TicketDTO selectedSeats = seatServiceClient.select(seatRequest);
            if (selectedSeats == null) {
                purchaseTicketVO.setStatus(RequestStatus.FAILED.toString());
                return purchaseTicketVO;
            }

            // 2. 调用订单服务
            OrderServiceClient.OrderCreateRemoteRequestDTO orderRequest = new OrderServiceClient.OrderCreateRemoteRequestDTO();
            orderRequest.setTrainNumber(purchaseTicketRequestDto.getTrainNum());
            orderRequest.setStartStation(purchaseTicketRequestDto.getStartStation());
            orderRequest.setEndStation(purchaseTicketRequestDto.getEndStation());

            List<OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO> orderItems = selectedSeats.getItems().stream().map(item -> {
                OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO orderItem = new OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO();
                orderItem.setPassengerId(item.getPassengerId());
                orderItem.setCarriageNumber(item.getCarriageNum());
                orderItem.setSeatNumber(item.getSeatNum());
                orderItem.setSeatType(item.getSeatType());
                orderItem.setAmount(new BigDecimal("100")); // TODO: 动态计算金额
                // TODO: 获取真实姓名和身份证号
                return orderItem;
            }).collect(Collectors.toList());
            orderRequest.setItems(orderItems);

            String orderSn = orderServiceClient.create(orderRequest);
            purchaseTicketVO.setOrderSn(orderSn);
            purchaseTicketVO.setStatus(RequestStatus.SUCCESS.toString());
            return purchaseTicketVO;
        }
    }

    @Override
    public PurchaseTicketVO check(String RequestId) {
        return null;
    }
}
