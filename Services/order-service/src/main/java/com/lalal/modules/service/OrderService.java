package com.lalal.modules.service;

import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.OrderListVO;
import com.lalal.modules.dto.response.PayOrderVO;

import java.util.List;
import java.util.Map;

public interface OrderService {
    String createOrder(OrderCreateRequestDTO request);

    OrderDetailVO getOrderDetail(String orderSn, String phone);

    PayOrderVO createPayForm(String orderSn, String phone);

    String handleAlipayNotify(Map<String, String> params);

    /**
     * 获取用户的历史订单列表
     */
    List<OrderListVO> getOrderList(String phone);

    /**
     * 退款（仅限已支付且未发车的订单）
     */
    void refundOrder(String orderSn, String phone);

    /**
     * 取消订单（仅限待支付的订单）
     */
    void cancelOrder(String orderSn, String phone);
    /**
     * 取消订单（仅限待支付的订单）内部接口
     */
    void cancelOrder(String orderSn);
}
