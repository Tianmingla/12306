package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.framework.idempotent.Idempotent;
import com.lalal.modules.constant.cache.WaitlistCacheConstant;
import com.lalal.modules.context.RequestContext;
import com.lalal.modules.dto.request.WaitlistCreateRequestDTO;
import com.lalal.modules.dto.response.WaitlistOrderVO;
import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.WaitlistOrderMapper;
import com.lalal.modules.service.PriorityCalculator;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.service.WaitlistService;
import com.lalal.modules.dto.WaitlistCheckMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 候补服务实现（增强版 - MQ驱动）
 *
 * <p>核心流程：
 * 1. 创建候补订单 → 计算优先级 → 入队 → 发送检查消息
 * 2. 定时任务扫描待兑现订单 → 发送检查消息
 * 3. WaitlistCheckConsumer 检查余票 → 有票则触发选座 → 创建订单
 * 4. WaitlistResultConsumer 处理结果 → 更新状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl extends ServiceImpl<WaitlistOrderMapper, WaitlistOrderDO>
        implements WaitlistService {

    private final WaitlistOrderMapper waitlistOrderMapper;
    private final WaitlistQueueService waitlistQueueService;
    private final PriorityCalculator priorityCalculator;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.lalal.framework.cache.SafeCacheTemplate safeCacheTemplate;
    private final com.lalal.modules.mq.MessageQueueService messageQueueService;

    private static final String WAITLIST_CHECK_TOPIC = "waitlist-check-topic";
    private static final String DEDUP_KEY_PREFIX = "WAITLIST:CREATE::";

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Idempotent(
            key =DEDUP_KEY_PREFIX+"${#request.account}:${#request.trainNumber}:${#request.travelDate}",
            message = "您已提交过候补订单，请勿重复提交",
            expire = 300
    )
    public String createWaitlist(WaitlistCreateRequestDTO request) {

        // 构建候补订单
        String waitlistSn = generateWaitlistSn();
        WaitlistOrderDO order = buildOrder(request, waitlistSn);
        this.save(order);

        // 计算初始优先级
        Long userOrderCount = getUserOrderCount(request.getAccount());
        Long queueSize = waitlistQueueService.getQueueSize(
                request.getTrainNumber(),
                request.getTravelDate().toString(),
                null);
        Integer vipLevel = getUserVipLevel(request.getAccount());

        BigDecimal priority = priorityCalculator.calculatePriority(
                order, vipLevel, userOrderCount, queueSize);

        // 4. 入队
        waitlistQueueService.enqueue(order, priority);

        // 5. 缓存幂等键（TTL = 截止时间 - 当前时间）
//        long ttlMinutes = Duration.between(
//                LocalDateTime.now(),
//                request.getDeadline().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
//        ).toMinutes();
//        safeCacheTemplate.set(dedupKey, waitlistSn, Math.max(ttlMinutes, 1), java.util.concurrent.TimeUnit.MINUTES);

        // 6. 发送候补检查消息（延迟5秒，避免与创建事务冲突）
        sendCheckMessage(order);

        log.info("[候补订单] 创建成功: waitlistSn={}, priority={}", waitlistSn, priority);
        return waitlistSn;
    }

    /**
     * 发送候补检查消息
     */
    private void sendCheckMessage(WaitlistOrderDO order) {
        String requestId = RequestContext.getRequestId();

        WaitlistCheckMessage msg = WaitlistCheckMessage.builder()
                .requestId(requestId)
                .waitlistSn(order.getWaitlistSn())
                .username(order.getUsername())
                .userId(getUserIdByUsername(order.getUsername()))
                .trainNumber(order.getTrainNumber())
                .startStation(order.getStartStation())
                .endStation(order.getEndStation())
                .travelDate(order.getTravelDate().toString())
                .seatTypes(parseStr(order.getSeatTypes(),Integer::parseInt))
                .priority(getQueuePosition(order.getWaitlistSn(),
                        order.getTrainNumber(), order.getTravelDate()).intValue())
                .passengerIds(parseStr(order.getPassengerIds(), Long::parseLong))
                .deadline(order.getDeadline())
                .prepayAmount(order.getPrepayAmount())
                .timestamp(System.currentTimeMillis())
                .source("WAITLIST")
                .build();

        // 延迟5秒发送
        messageQueueService.sendDelay(WAITLIST_CHECK_TOPIC, "check", msg, 5000);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelWaitlist(String waitlistSn, String username) {
        WaitlistOrderDO order = findByWaitlistSn(waitlistSn);

        if (order == null) {
            throw new IllegalArgumentException("候补订单不存在");
        }

        if (!username.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权操作该候补订单");
        }

        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new IllegalStateException("当前状态不可取消");
        }

        // 更新状态
        order.setStatus(3); // 已取消
        this.updateById(order);

        // 从队列移除
        waitlistQueueService.remove(waitlistSn,
                order.getTrainNumber(),
                order.getTravelDate().toString());

        log.info("[候补订单] 取消成功: waitlistSn={}", waitlistSn);
    }

    @Override
    public List<WaitlistOrderVO> getWaitlistOrders(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WaitlistOrderDO>();
        wrapper.eq(WaitlistOrderDO::getUsername, username);
        wrapper.orderByDesc(WaitlistOrderDO::getCreateTime);

        return this.list(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public WaitlistOrderVO getWaitlistDetail(String waitlistSn, String username) {
        WaitlistOrderDO order = findByWaitlistSn(waitlistSn);

        if (order == null) {
            throw new IllegalArgumentException("候补订单不存在");
        }

        if (!username.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权查看该候补订单");
        }

        return toVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndFulfillWaitlistOrders() {
        log.debug("[候补订单] 开始批量扫描待兑现订单...");

        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WaitlistOrderDO>();
        wrapper.eq(WaitlistOrderDO::getStatus, 0); // 待兑现
        wrapper.gt(WaitlistOrderDO::getDeadline, new Date());
        wrapper.orderByAsc(WaitlistOrderDO::getCreateTime); // 先到先得

        List<WaitlistOrderDO> pendingOrders = this.list(wrapper);

        for (WaitlistOrderDO order : pendingOrders) {
            try {
                // 发送检查消息（幂等性由消费者保证）
                sendCheckMessage(order);
            } catch (Exception e) {
                log.error("[候补订单] 发送检查消息失败: waitlistSn={}", order.getWaitlistSn(), e);
            }
        }

        // 处理过期订单
        handleExpiredOrders();

        log.debug("[候补订单] 批量扫描完成，共 {} 个订单", pendingOrders.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWaitlistStatus(String waitlistSn, Integer status) {
        WaitlistOrderDO order = findByWaitlistSn(waitlistSn);
        if (order != null) {
            order.setStatus(status);
            this.updateById(order);
            log.info("[候补订单] 状态更新: waitlistSn={}, status={}", waitlistSn, status);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWaitlistStatus(String waitlistSn, Integer status, String orderSn) {
        WaitlistOrderDO order = findByWaitlistSn(waitlistSn);
        if (order != null) {
            order.setStatus(status);
            order.setFulfilledOrderSn(orderSn);
            this.updateById(order);
            log.info("[候补订单] 状态更新: waitlistSn={}, status={}, orderSn={}",
                    waitlistSn, status, orderSn);
        }
    }

    /**
     * 处理过期订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleExpiredOrders() {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WaitlistOrderDO>();
        wrapper.in(WaitlistOrderDO::getStatus, 0, 1); // 待兑现或兑现中
        wrapper.lt(WaitlistOrderDO::getDeadline, new Date());

        List<WaitlistOrderDO> expiredOrders = this.list(wrapper);

        for (WaitlistOrderDO order : expiredOrders) {
            order.setStatus(4); // 已过期
            this.updateById(order);

            // 从队列移除
            waitlistQueueService.remove(order.getWaitlistSn(),
                    order.getTrainNumber(),
                    order.getTravelDate().toString());

            log.info("[候补订单] 已过期: waitlistSn={}", order.getWaitlistSn());
        }
    }

    /**
     * 重新计算优先级
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculatePriority(WaitlistOrderDO order) {
        Long userOrderCount = getUserOrderCount(order.getUsername());
        Long queueSize = waitlistQueueService.getQueueSize(
                order.getTrainNumber(), order.getTravelDate().toString(), null);
        Integer vipLevel = getUserVipLevel(order.getUsername());

        BigDecimal priority = priorityCalculator.calculatePriority(
                order, vipLevel, userOrderCount, queueSize);

        waitlistQueueService.updatePriority(
                order.getWaitlistSn(),
                order.getTrainNumber(),
                order.getTravelDate().toString(),
                priority);
    }

    /**
     * 失败惩罚：降低优先级
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculatePriorityWithPenalty(WaitlistOrderDO order) {
        // 简化：失败一次直接降低固定分数10分
        String key = WaitlistCacheConstant.waitlistQueueKey(
                order.getTrainNumber(), order.getTravelDate().toString(), null);
        Double currentScore = stringRedisTemplate.opsForZSet().score(key, order.getWaitlistSn());
        if (currentScore != null) {
            BigDecimal newPriority = BigDecimal.valueOf(currentScore - 10);
            waitlistQueueService.updatePriority(
                    order.getWaitlistSn(),
                    order.getTrainNumber(),
                    order.getTravelDate().toString(),
                    newPriority);
            log.info("[候补订单] 优先级惩罚: waitlistSn={}, old={}, new={}",
                    order.getWaitlistSn(), currentScore, newPriority);
        }
    }

    /**
     * 根据候补订单号查询
     */
    public WaitlistOrderDO findByWaitlistSn(String waitlistSn) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WaitlistOrderDO>();
        wrapper.eq(WaitlistOrderDO::getWaitlistSn, waitlistSn);
        wrapper.eq(WaitlistOrderDO::getDelFlag, 0);
        return this.getOne(wrapper);
    }

    // ==================== 工具方法 ====================

    private String generateWaitlistSn() {
        return "WL" + java.util.UUID.randomUUID()
                .toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private WaitlistOrderDO buildOrder(WaitlistCreateRequestDTO request, String waitlistSn) {
        WaitlistOrderDO order = new WaitlistOrderDO();
        order.setWaitlistSn(waitlistSn);
        order.setUsername(request.getAccount());
        order.setTrainNumber(request.getTrainNumber());
        order.setStartStation(request.getStartStation());
        order.setEndStation(request.getEndStation());
        order.setTravelDate(request.getTravelDate());
        order.setSeatTypes(request.getSeatTypes().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        order.setPassengerIds(request.getPassengerIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        order.setPrepayAmount(request.getPrepayAmount());
        order.setDeadline(request.getDeadline());
        order.setStatus(0); // 待兑现

        return order;
    }

    private <T> java.util.List<T> parseStr(String str, Function<String,T> callback) {
        if (str == null || str.trim().isEmpty()) {
            return List.of();
        }
        String[] parts = str.split(",");
        java.util.List<T> result = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                result.add(callback.apply(part));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return result;
    }

    private Long getQueuePosition(String waitlistSn, String trainNumber, java.time.LocalDate travelDate) {
        return waitlistQueueService.getQueuePosition(waitlistSn, trainNumber, travelDate.toString());
    }

    private Long getUserOrderCount(String username) {
        // 简化：暂不查询历史订单，固定返回0
        return 0L;
    }

    private Integer getUserVipLevel(String username) {
        // 简化：所有人同一等级，固定返回0（普通用户）
        return 0;
    }

    private Long getUserIdByUsername(String username) {
        // 简化：返回固定用户ID（实际应从t_user表查询）
        return 1L;
    }

    private Integer getFailureCount(String waitlistSn) {
        // 简化：暂不查询失败次数，固定返回0
        return 0;
    }

    private WaitlistOrderVO toVO(WaitlistOrderDO order) {
        Long queuePos = getQueuePosition(order.getWaitlistSn(),
                order.getTrainNumber(), order.getTravelDate());

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
                .queuePosition(queuePos != null ? queuePos.intValue() : null)
                .successRate(estimateSuccessRate(order))
                .build();
    }

    private String getSeatTypesText(String seatTypes) {
        if (seatTypes == null || seatTypes.isBlank()) {
            return "未知";
        }
        String[] types = seatTypes.split(",");
        StringBuilder sb = new StringBuilder();
        for (String type : types) {
            try {
                int seatType = Integer.parseInt(type.trim());
                sb.append(getSeatTypeName(seatType)).append("、");
            } catch (NumberFormatException ignored) {}
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String getSeatTypeName(int seatType) {
        return SeatType.getDescByCode(seatType);
    }

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

    private Integer estimateSuccessRate(WaitlistOrderDO order) {
        // TODO: 根据历史数据、退票率等计算
        return 65;
    }
}
