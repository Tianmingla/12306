package com.lalal.modules.service;

import com.lalal.modules.dto.request.OrderCreateRequestDTO;

public interface OrderService {
    String createOrder(OrderCreateRequestDTO request);
}
