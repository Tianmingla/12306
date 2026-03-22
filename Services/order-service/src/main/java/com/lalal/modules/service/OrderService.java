package com.lalal.modules.service;

import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.PayOrderVO;

import java.util.Map;

public interface OrderService {
    String createOrder(OrderCreateRequestDTO request);

    OrderDetailVO getOrderDetail(String orderSn, String phone);

    PayOrderVO createPayForm(String orderSn, String phone);

    String handleAlipayNotify(Map<String, String> params);
}
