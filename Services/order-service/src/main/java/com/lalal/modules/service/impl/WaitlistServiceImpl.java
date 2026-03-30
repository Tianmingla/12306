/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.request.WaitlistCreateRequestDTO;
import com.lalal.modules.dto.response.WaitlistOrderVO;
import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.mapper.WaitlistOrderMapper;
import com.lalal.modules.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 候补购票服务实现
 *
 * 候补购票流程：
 * 1. 用户提交候补请求（车次、日期、座位类型、乘车人）
 * 2. 系统预扣款（冻结金额）
 * 3. 定时任务检查是否有票
 * 4. 有票时自动为用户购票
 * 5. 购票成功后通知用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl extends ServiceImpl<WaitlistOrderMapper, WaitlistOrderDO> implements WaitlistService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createWaitlist(WaitlistCreateRequestDTO request) {
        // 生成候补订单号
        String waitlistSn = "WL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // 构建候补订单
        WaitlistOrderDO waitlistOrder = new WaitlistOrderDO();
        waitlistOrder.setWaitlistSn(waitlistSn);
        waitlistOrder.setUsername(request.getAccount());
        waitlistOrder.setTrainNumber(request.getTrainNumber());
        waitlistOrder.setStartStation(request.getStartStation());
        waitlistOrder.setEndStation(request.getEndStation());
        waitlistOrder.setTravelDate(request.getTravelDate());
        waitlistOrder.setSeatTypes(request.getSeatTypes().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        waitlistOrder.setPassengerIds(request.getPassengerIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        waitlistOrder.setPrepayAmount(request.getPrepayAmount());
        waitlistOrder.setDeadline(request.getDeadline());
        waitlistOrder.setStatus(0); // 待兑现

        this.save(waitlistOrder);

        log.info("创建候补订单成功：{}，车次：{}，用户：{}", waitlistSn, request.getTrainNumber(), request.getAccount());

        return waitlistSn;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelWaitlist(String waitlistSn, String username) {
        WaitlistOrderDO waitlistOrder = findByWaitlistSn(waitlistSn);

        if (waitlistOrder == null) {
            throw new IllegalArgumentException("候补订单不存在");
        }

        if (!StringUtils.hasText(username) || !username.equals(waitlistOrder.getUsername())) {
            throw new IllegalArgumentException("无权操作该候补订单");
        }

        if (waitlistOrder.getStatus() != 0 && waitlistOrder.getStatus() != 1) {
            throw new IllegalStateException("当前状态不可取消");
        }

        waitlistOrder.setStatus(3); // 已取消
        this.updateById(waitlistOrder);

        log.info("取消候补订单：{}，用户：{}", waitlistSn, username);
    }

    @Override
    public List<WaitlistOrderVO> getWaitlistOrders(String username) {
        if (!StringUtils.hasText(username)) {
            return List.of();
        }

        LambdaQueryWrapper<WaitlistOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WaitlistOrderDO::getUsername, username);
        wrapper.orderByDesc(WaitlistOrderDO::getCreateTime);

        List<WaitlistOrderDO> orders = this.list(wrapper);

        return orders.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public WaitlistOrderVO getWaitlistDetail(String waitlistSn, String username) {
        WaitlistOrderDO waitlistOrder = findByWaitlistSn(waitlistSn);

        if (waitlistOrder == null) {
            throw new IllegalArgumentException("候补订单不存在");
        }

        if (!StringUtils.hasText(username) || !username.equals(waitlistOrder.getUsername())) {
            throw new IllegalArgumentException("无权查看该候补订单");
        }

        return toVO(waitlistOrder);
    }

    /**
     * 定时任务：检查并兑现候补订单
     * 每5分钟执行一次
     */
    @Override
    @Scheduled(fixedRate = 300000)
    @Transactional(rollbackFor = Exception.class)
    public void checkAndFulfillWaitlistOrders() {
        log.debug("开始检查候补订单...");

        // 查询待兑现和兑现中的订单
        LambdaQueryWrapper<WaitlistOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WaitlistOrderDO::getStatus, 0, 1);
        wrapper.gt(WaitlistOrderDO::getDeadline, new Date());

        List<WaitlistOrderDO> pendingOrders = this.list(wrapper);

        for (WaitlistOrderDO order : pendingOrders) {
            try {
                // TODO: 调用 ticket-service 检查余票
                // 如果有余票，调用购票接口
                // 购票成功后更新状态
                boolean hasTicket = checkTicketAvailability(order);

                if (hasTicket) {
                    // 更新状态为兑现中
                    order.setStatus(1);
                    this.updateById(order);

                    // 执行购票
                    String orderSn = fulfillOrder(order);

                    if (orderSn != null) {
                        order.setStatus(2); // 已兑现
                        order.setFulfilledOrderSn(orderSn);
                        this.updateById(order);
                        log.info("候补订单兑现成功：{}，订单号：{}", order.getWaitlistSn(), orderSn);
                    }
                }
            } catch (Exception e) {
                log.error("处理候补订单异常：{}", order.getWaitlistSn(), e);
            }
        }

        // 处理过期订单
        handleExpiredOrders();
    }

    /**
     * 检查票是否可用
     */
    private boolean checkTicketAvailability(WaitlistOrderDO order) {
        // TODO: 实际调用 ticket-service 或 seat-service 检查余票
        // 这里简化处理，随机返回结果用于演示
        return Math.random() < 0.1; // 10% 概率有票
    }

    /**
     * 执行购票
     */
    private String fulfillOrder(WaitlistOrderDO order) {
        // TODO: 调用实际的购票流程
        // 这里返回模拟的订单号
        return "ORD" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * 处理过期订单
     */
    private void handleExpiredOrders() {
        LambdaQueryWrapper<WaitlistOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WaitlistOrderDO::getStatus, 0, 1);
        wrapper.lt(WaitlistOrderDO::getDeadline, new Date());

        List<WaitlistOrderDO> expiredOrders = this.list(wrapper);

        for (WaitlistOrderDO order : expiredOrders) {
            order.setStatus(4); // 已过期
            this.updateById(order);
            log.info("候补订单已过期：{}", order.getWaitlistSn());
        }
    }

    /**
     * 根据候补订单号查询
     */
    private WaitlistOrderDO findByWaitlistSn(String waitlistSn) {
        LambdaQueryWrapper<WaitlistOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WaitlistOrderDO::getWaitlistSn, waitlistSn);
        return this.getOne(wrapper);
    }

    /**
     * 转换为 VO
     */
    private WaitlistOrderVO toVO(WaitlistOrderDO order) {
        return WaitlistOrderVO.builder()
                .id(order.getId())
                .waitlistSn(order.getWaitlistSn())
                .trainNumber(order.getTrainNumber())
                .startStation(order.getStartStation())
                .endStation(order.getEndStation())
                .travelDate(order.getTravelDate())
                .seatTypesText(getSeatTypesText(order.getSeatTypes()))
                .prepayAmount(order.getPrepayAmount())
                .deadline(order.getDeadline())
                .status(order.getStatus())
                .statusText(getStatusText(order.getStatus()))
                .fulfilledOrderSn(order.getFulfilledOrderSn())
                .createTime(order.getCreateTime())
                .queuePosition(estimateQueuePosition(order))
                .successRate(estimateSuccessRate(order))
                .build();
    }

    /**
     * 获取座位类型文本
     */
    private String getSeatTypesText(String seatTypes) {
        if (!StringUtils.hasText(seatTypes)) {
            return "未知";
        }

        String[] types = seatTypes.split(",");
        StringBuilder sb = new StringBuilder();

        for (String type : types) {
            try {
                int seatType = Integer.parseInt(type.trim());
                sb.append(getSeatTypeName(seatType)).append("、");
            } catch (NumberFormatException e) {
                // 忽略
            }
        }

        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * 获取座位类型名称
     */
    private String getSeatTypeName(int seatType) {
        return switch (seatType) {
            case 0 -> "硬座";
            case 1 -> "二等座";
            case 2 -> "一等座";
            case 3 -> "商务座";
            case 4 -> "软座";
            case 5 -> "硬卧";
            case 6 -> "软卧";
            default -> "其他";
        };
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待兑现";
            case 1 -> "兑现中";
            case 2 -> "已兑现";
            case 3 -> "已取消";
            case 4 -> "已过期";
            default -> "未知";
        };
    }

    /**
     * 估算排队位置
     */
    private Integer estimateQueuePosition(WaitlistOrderDO order) {
        // TODO: 根据实际候补队列计算
        // 这里简化处理
        LambdaQueryWrapper<WaitlistOrderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WaitlistOrderDO::getTrainNumber, order.getTrainNumber());
        wrapper.eq(WaitlistOrderDO::getTravelDate, order.getTravelDate());
        wrapper.lt(WaitlistOrderDO::getCreateTime, order.getCreateTime());
        wrapper.in(WaitlistOrderDO::getStatus, 0, 1);

        Long count = this.count(wrapper);
        return count != null ? count.intValue() + 1 : 1;
    }

    /**
     * 估算成功率
     */
    private Integer estimateSuccessRate(WaitlistOrderDO order) {
        // TODO: 根据历史数据、退票率等计算
        // 这里简化处理，返回固定值
        return 65; // 65% 成功率
    }
}
