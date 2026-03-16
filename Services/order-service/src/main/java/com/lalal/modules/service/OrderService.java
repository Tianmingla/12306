package com.lalal.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.entity.OrderDO;

public interface OrderService extends IService<OrderDO> {
    String createOrder(OrderCreateRequestDTO request);
}
