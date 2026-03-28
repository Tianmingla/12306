package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.OrderDO;
import com.lalal.modules.admin.dto.OrderQueryRequest;
import com.lalal.modules.admin.service.AdminOrderService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/order")
public class AdminOrderController {

    @Autowired
    private AdminOrderService adminOrderService;

    /**
     * 分页查询订单列表
     */
    @GetMapping("/list")
    public Result<PageResult<OrderDO>> listOrders(OrderQueryRequest request) {
        return Result.success(adminOrderService.listOrders(request));
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{orderSn}")
    public Result<OrderDO> getOrderDetail(@PathVariable String orderSn) {
        return Result.success(adminOrderService.getByOrderSn(orderSn));
    }
}
