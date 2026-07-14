package com.lalal.modules.feign;

import com.lalal.modules.dto.FeignResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务 Feign 客户端
 */
@FeignClient(name = "order-service")
public interface OrderFeignClient {

    /**
     * 查询订单详情
     */
    @GetMapping("/api/order/detail/{orderSn}")
    FeignResult getOrderDetail(@PathVariable("orderSn") String orderSn);

    /**
     * 查询订单列表
     */
    @GetMapping("/api/order/list")
    FeignResult getOrderList();

    /**
     * 退票
     */
    @PostMapping("/api/order/refund/{orderSn}")
    FeignResult refundOrder(@PathVariable("orderSn") String orderSn);

    /**
     * 取消订单
     */
    @PostMapping("/api/order/cancel/{orderSn}")
    FeignResult cancelOrder(@PathVariable("orderSn") String orderSn);
}
