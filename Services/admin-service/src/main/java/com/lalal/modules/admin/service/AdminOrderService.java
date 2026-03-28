package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.OrderDO;
import com.lalal.modules.admin.dto.OrderQueryRequest;
import com.lalal.modules.dto.PageResult;

public interface AdminOrderService {

    /**
     * 分页查询订单列表
     */
    PageResult<OrderDO> listOrders(OrderQueryRequest request);

    /**
     * 根据订单号查询订单
     */
    OrderDO getByOrderSn(String orderSn);
}
