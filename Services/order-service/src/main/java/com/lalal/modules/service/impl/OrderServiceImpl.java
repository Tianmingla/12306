package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.entity.OrderDO;
import com.lalal.modules.entity.OrderItemDO;
import com.lalal.modules.mapper.OrderItemMapper;
import com.lalal.modules.mapper.OrderMapper;
import com.lalal.modules.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderDO> implements OrderService {

    private final OrderItemMapper orderItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateRequestDTO request) {
        String orderSn = UUID.randomUUID().toString().replace("-", "");
        
        OrderDO orderDO = new OrderDO();
        orderDO.setOrderSn(orderSn);
        orderDO.setTrainNumber(request.getTrainNumber());
        orderDO.setStartStation(request.getStartStation());
        orderDO.setEndStation(request.getEndStation());
        orderDO.setStatus(0); // 待支付
        // totalAmount calculation could be added here
        this.save(orderDO);

        for (OrderCreateRequestDTO.OrderItemRequestDTO item : request.getItems()) {
            OrderItemDO itemDO = new OrderItemDO();
            itemDO.setOrderId(orderDO.getId());
            itemDO.setOrderSn(orderSn);
            itemDO.setCarriageNumber(item.getCarriageNumber());
            itemDO.setSeatNumber(item.getSeatNumber());
            itemDO.setSeatType(item.getSeatType());
            itemDO.setAmount(item.getAmount());
            itemDO.setIdCard(item.getIdCard());
            orderItemMapper.insert(itemDO);
        }

        return orderSn;
    }
}
