package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.modules.admin.dao.OrderDO;
import com.lalal.modules.admin.dto.OrderQueryRequest;
import com.lalal.modules.admin.mapper.OrderMapper;
import com.lalal.modules.admin.service.AdminOrderService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public PageResult<OrderDO> listOrders(OrderQueryRequest request) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<>();

        // 游标分页
        if (request.getLastId() != null && request.getLastId() > 0) {
            wrapper.gt(OrderDO::getId, request.getLastId());
        }

        // 订单号精确匹配
        if (request.getOrderSn() != null && !request.getOrderSn().isEmpty()) {
            wrapper.eq(OrderDO::getOrderSn, request.getOrderSn());
        }

        // 关键字搜索（用户名/车次）
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            wrapper.and(w -> w.like(OrderDO::getUsername, request.getKeyword())
                    .or()
                    .like(OrderDO::getTrainNumber, request.getKeyword()));
        }

        // 订单状态筛选
        if (request.getStatus() != null) {
            wrapper.eq(OrderDO::getStatus, request.getStatus());
        }

        // 逻辑删除过滤
        wrapper.eq(OrderDO::getDelFlag, 0);

        // 按ID降序（最新订单在前）
        wrapper.orderByDesc(OrderDO::getId);

        int limit = request.getPageSize() + 1;
        wrapper.last("LIMIT " + limit);

        List<OrderDO> list = orderMapper.selectList(wrapper);

        boolean hasMore = list.size() > request.getPageSize();
        if (hasMore) {
            list = list.subList(0, request.getPageSize());
        }

        Long nextId = list.isEmpty() ? null : list.get(list.size() - 1).getId();

        return PageResult.of(list, null, nextId, hasMore);
    }

    @Override
    public OrderDO getByOrderSn(String orderSn) {
        LambdaQueryWrapper<OrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDO::getOrderSn, orderSn)
                .eq(OrderDO::getDelFlag, 0);
        return orderMapper.selectOne(wrapper);
    }
}
