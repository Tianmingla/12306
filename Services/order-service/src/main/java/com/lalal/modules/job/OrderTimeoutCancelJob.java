package com.lalal.modules.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lalal.modules.entity.OrderDO;
import com.lalal.modules.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 * 每分钟检查一次，取消超时未支付的订单
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutCancelJob {

    private final OrderMapper orderMapper;

    /**
     * 订单超时时间（毫秒），默认30分钟
     */
    private static final long ORDER_TIMEOUT_MS = 30 * 60 * 1000;

    /**
     * 每分钟执行一次，检查超时订单
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void cancelTimeoutOrders() {
        Date timeoutThreshold = new Date(System.currentTimeMillis() - ORDER_TIMEOUT_MS);

        // 查询超时未支付的订单
        LambdaQueryWrapper<OrderDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderDO::getStatus, 0) // 待支付
                .lt(OrderDO::getCreateTime, timeoutThreshold);

        List<OrderDO> timeoutOrders = orderMapper.selectList(queryWrapper);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时未支付订单，准备取消", timeoutOrders.size());

        // 批量更新状态为已取消
        for (OrderDO order : timeoutOrders) {
            LambdaUpdateWrapper<OrderDO> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(OrderDO::getId, order.getId())
                    .eq(OrderDO::getStatus, 0) // 确保还是待支付状态
                    .set(OrderDO::getStatus, 2); // 已取消

            int updated = orderMapper.update(null, updateWrapper);
            if (updated > 0) {
                log.info("订单 {} 已超时取消", order.getOrderSn());
            }
        }
    }
}
