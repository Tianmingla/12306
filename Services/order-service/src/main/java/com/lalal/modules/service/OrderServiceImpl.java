package com.lalal.modules.service;

import com.lalal.modules.dto.OrderCreateRequestDTO;
import com.lalal.modules.entity.OrderDO;
import com.lalal.modules.entity.OrderItemDO;
import com.lalal.modules.mapper.OrderItemMapper;
import com.lalal.modules.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional
    public String createOrder(OrderCreateRequestDTO request) {
        String orderSn = UUID.randomUUID().toString().replace("-", "");
        
        OrderDO orderDO = new OrderDO();
        orderDO.setOrderSn(orderSn);
        orderDO.setTrainNumber(request.getTrainNumber());
        orderDO.setStartStation(request.getStartStation());
        orderDO.setEndStation(request.getEndStation());
        orderDO.setStatus(0); // 待支付
        orderDO.setCreateTime(LocalDateTime.now());
        orderDO.setUpdateTime(LocalDateTime.now());
        
        BigDecimal totalAmount = request.getItems().stream()
                .map(OrderCreateRequestDTO.OrderItemRequestDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orderDO.setTotalAmount(totalAmount);
        
        orderMapper.insert(orderDO);
        
        for (OrderCreateRequestDTO.OrderItemRequestDTO item : request.getItems()) {
            OrderItemDO orderItemDO = new OrderItemDO();
            orderItemDO.setOrderId(orderDO.getId());
            orderItemDO.setOrderSn(orderSn);
            orderItemDO.setPassengerId(item.getPassengerId());
            orderItemDO.setPassengerName(item.getRealName());
            orderItemDO.setIdCard(item.getIdCard());
            orderItemDO.setCarriageNumber(item.getCarriageNumber());
            orderItemDO.setSeatNumber(item.getSeatNumber());
            orderItemDO.setSeatType(item.getSeatType());
            orderItemDO.setAmount(item.getAmount());
            orderItemDO.setCreateTime(LocalDateTime.now());
            orderItemDO.setUpdateTime(LocalDateTime.now());
            orderItemMapper.insert(orderItemDO);
        }
        
        return orderSn;
    }
}
