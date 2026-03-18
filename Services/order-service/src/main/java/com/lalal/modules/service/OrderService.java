package com.lalal.modules.service;

import com.lalal.modules.dto.OrderCreateRequestDTO;

public interface OrderService {
    String createOrder(OrderCreateRequestDTO request);
}
