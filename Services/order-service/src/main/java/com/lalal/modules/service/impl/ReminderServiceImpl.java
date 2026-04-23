package com.lalal.modules.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.ReminderMessage;
import com.lalal.modules.dto.ReminderState;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 出行提醒服务实现
 *
 * 核心设计：
 * 1. 订单创建时初始化 ReminderState 并发送延迟消息
 * 2. 延迟消息到期后，校验版本号再发送短信
 * 3. 晚点/停运时更新缓存状态，触发重新调度
 *
 * 消息体版本控制流程：
 * 1. 订单创建 → 发送版本=v1 的延迟消息
 * 2. 晚点发生 → 更新状态 version=v2, delayMinutes=30
 * 3. 延迟消息到期 → 版本=v1 ≠ 缓存 version=v2 → 重新调度新消息
 * 4. 新消息到期 → 版本=v2 == 缓存 version=v2 → 发送短信
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;

    private static final String REMINDER_TOPIC = "travel-reminder-topic";
    private static final int DEFAULT_TTL_DAYS = 7;

    // 配置（从 application.yml 读取）
    @Value("${reminder.enabled:true}")
    private boolean reminderEnabled;

    @Value("${reminder.depart-before-1h-minutes:60}")
    private int before1hMinutes;

    @Value("${reminder.depart-before-30m-minutes:30}")
    private int before30mMinutes;

    // ==================== 初始化提醒 ====================

    @Override
    public void initReminderState(String orderSn, String trainNumber, String runDate,
                                  String departureStation, String arrivalStation,
                                  String username, String passengerName,
                                  Long planDepartTime, Long planArrivalTime) {
        if (!reminderEnabled) {
            log.info("[提醒] 功能已禁用，跳过初始化: orderSn={}", orderSn);
            return;
        }

        log.info("[提醒] 初始化提醒状态: orderSn={}, trainNumber={}, departTime={}",
                orderSn, trainNumber, planDepartTime);

        // 1. 创建提醒状态
        ReminderState state = ReminderState.builder()
                .planDepartTime(planDepartTime)
                .planArrivalTime(planArrivalTime)
                .version(1)
                .status(ReminderState.STATUS_NORMAL)
                .delayMinutes(0)
                .sentFlags(0)
                .trainNumber(trainNumber)
                .runDate(runDate)
                .username(username)
                .build();

        // 2. 写入缓存
        String stateKey = CacheConstant.reminderStateKey(orderSn);
        safeCacheTemplate.safeSet(stateKey, state, DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        // 3. 发送延迟提醒消息（发车前1小时）
        sendDelayReminder(orderSn, trainNumber, runDate, departureStation, arrivalStation,
                username, passengerName, planDepartTime, planArrivalTime,
                1, ReminderMessage.TYPE_BEFORE_DEPART_1H,
                planDepartTime - before1hMinutes * 60 * 1000L);

        // 4. 发送延迟提醒消息（发车前30分钟）
        sendDelayReminder(orderSn, trainNumber, runDate, departureStation, arrivalStation,
                username, passengerName, planDepartTime, planArrivalTime,
                1, ReminderMessage.TYPE_BEFORE_DEPART_30M,
                planDepartTime - before30mMinutes * 60 * 1000L);

        // 5. 发送延迟提醒消息（到达提醒）
        sendDelayReminder(orderSn, trainNumber, runDate, departureStation, arrivalStation,
                username, passengerName, planDepartTime, planArrivalTime,
                1, ReminderMessage.TYPE_ARRIVAL,
                planArrivalTime + 10 * 60 * 1000L); // 到达后10分钟

        log.info("[提醒] 初始化完成，已发送3条延迟消息: orderSn={}", orderSn);
    }

    /**
     * 发送延迟提醒消息
     */
    private void sendDelayReminder(String orderSn, String trainNumber, String runDate,
                                   String departureStation, String arrivalStation,
                                   String username, String passengerName,
                                   Long planDepartTime, Long planArrivalTime,
                                   int version, int reminderType, long triggerTime) {

        // 计算延迟时间（从现在到触发时间）
        long delayMillis = triggerTime - System.currentTimeMillis();
        if (delayMillis < 0) {
            log.warn("[提醒] 触发时间已过，跳过: orderSn={}, type={}, triggerTime={}",
                    orderSn, reminderType, triggerTime);
            return;
        }

        ReminderMessage message = ReminderMessage.builder()
                .requestId(orderSn + "_" + reminderType)
                .orderSn(orderSn)
                .trainNumber(trainNumber)
                .runDate(runDate)
                .departureStation(departureStation)
                .arrivalStation(arrivalStation)
                .username(username)
                .passengerName(passengerName)
                .planDepartTime(planDepartTime)
                .planArrivalTime(planArrivalTime)
                .version(version)
                .reminderType(reminderType)
                .triggerTime(triggerTime)
                .build();

        // 发送延迟消息
        messageQueueService.sendDelay(REMINDER_TOPIC, message, delayMillis);
        log.info("[提醒] 发送延迟消息: orderSn={}, type={}, delay={}ms, triggerTime={}",
                orderSn, reminderType, delayMillis, triggerTime);
    }

    // ==================== 消费提醒消息 ====================

    @Override
    public void processReminder(ReminderMessage message) {
        String orderSn = message.getOrderSn();
        int msgVersion = message.getVersion();

        log.info("[提醒] 收到提醒消息: orderSn={}, type={}, version={}, triggerTime={}",
                orderSn, message.getReminderType(), msgVersion, message.getTriggerTime());

        // 1. 获取缓存中的状态
        String stateKey = CacheConstant.reminderStateKey(orderSn);
        ReminderState state = safeCacheTemplate.get(stateKey, new TypeReference<ReminderState>() {});

        if (state == null) {
            log.warn("[提醒] 提醒状态已过期或不存在: orderSn={}", orderSn);
            return;
        }

        // 2. 版本校验
        if (!msgVersion.equals(state.getVersion())) {
            log.info("[提醒] 版本不匹配，重新调度: orderSn={}, msgVersion={}, cacheVersion={}",
                    orderSn, msgVersion, state.getVersion());
            rescheduleReminder(message, state);
            return;
        }

        // 3. 状态校验：停运
        if (state.getStatus() == ReminderState.STATUS_CANCEL) {
            log.info("[提醒] 列车已停运，取消提醒: orderSn={}", orderSn);
            // 发送停运通知
            sendSms(state.getUsername(),
                    String.format("【12306】很抱歉，您购买的%s次列车因故停运，请关注后续通知。",
                            state.getTrainNumber()));
            return;
        }

        // 4. 检查是否已发送
        int flag = getFlagForType(message.getReminderType());
        if (state.hasSent(flag)) {
            log.info("[提醒] 提醒已发送过，跳过: orderSn={}, type={}", orderSn, message.getReminderType());
            return;
        }

        // 5. 发送短信
        String smsContent = buildSmsContent(message, state);
        sendSms(state.getUsername(), smsContent);

        // 6. 标记已发送
        state.markSent(flag);
        safeCacheTemplate.safeSet(stateKey, state, DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        log.info("[提醒] 短信发送成功: orderSn={}, type={}, phone={}***",
                orderSn, message.getReminderType(),
                state.getUsername() != null && state.getUsername().length() > 7
                        ? state.getUsername().substring(0, 3) : "***");
    }

    /**
     * 根据提醒类型获取发送标记
     */
    private int getFlagForType(int reminderType) {
        return switch (reminderType) {
            case ReminderMessage.TYPE_BEFORE_DEPART_1H -> ReminderState.FLAG_1H_SENT;
            case ReminderMessage.TYPE_BEFORE_DEPART_30M -> ReminderState.FLAG_30M_SENT;
            case ReminderMessage.TYPE_ARRIVAL -> ReminderState.FLAG_ARRIVAL_SENT;
            default -> 0;
        };
    }

    /**
     * 重新调度提醒
     */
    private void rescheduleReminder(ReminderMessage oldMessage, ReminderState newState) {
        // 计算新的触发时间
        long newTriggerTime = switch (oldMessage.getReminderType()) {
            case ReminderMessage.TYPE_BEFORE_DEPART_1H ->
                    newState.getPlanDepartTime() - before1hMinutes * 60 * 1000L;
            case ReminderMessage.TYPE_BEFORE_DEPART_30M ->
                    newState.getPlanDepartTime() - before30mMinutes * 60 * 1000L;
            case ReminderMessage.TYPE_ARRIVAL ->
                    newState.getPlanArrivalTime() + 10 * 60 * 1000L;
            default -> oldMessage.getTriggerTime();
        };

        // 重新发送延迟消息（使用新版本号）
        sendDelayReminder(oldMessage.getOrderSn(), newState.getTrainNumber(), newState.getRunDate(),
                oldMessage.getDepartureStation(), oldMessage.getArrivalStation(),
                newState.getUsername(), oldMessage.getPassengerName(),
                newState.getPlanDepartTime(), newState.getPlanArrivalTime(),
                newState.getVersion(), oldMessage.getReminderType(), newTriggerTime);

        log.info("[提醒] 重新调度完成: orderSn={}, oldVersion={}, newVersion={}, newTriggerTime={}",
                oldMessage.getOrderSn(), oldMessage.getVersion(), newState.getVersion(), newTriggerTime);
    }

    /**
     * 构建短信内容
     */
    private String buildSmsContent(ReminderMessage message, ReminderState state) {
        String trainNumber = state.getTrainNumber();
        long departTime = state.getPlanDepartTime();
        long arrivalTime = state.getPlanArrivalTime();

        String departTimeStr = formatTime(departTime);
        String arrivalTimeStr = formatTime(arrivalTime);

        return switch (message.getReminderType()) {
            case ReminderMessage.TYPE_BEFORE_DEPART_1H ->
                    String.format("【12306】尊敬的乘客，您的%s次列车将于1小时后发车，请提前到达%s站检票乘车。",
                            trainNumber, message.getDepartureStation());
            case ReminderMessage.TYPE_BEFORE_DEPART_30M ->
                    String.format("【12306】温馨提醒：%s次列车将于30分钟后发车，请带好身份证从%s站检票口检票。",
                            trainNumber, message.getDepartureStation());
            case ReminderMessage.TYPE_ARRIVAL ->
                    String.format("【12306】您乘坐的%s次列车预计%s到达%s站，请带好随身物品准备下车。",
                            trainNumber, arrivalTimeStr, message.getArrivalStation());
            default ->
                    String.format("【12306】您的%s次列车信息：%s出发，%s到达。",
                            trainNumber, departTimeStr, arrivalTimeStr);
        };
    }

    /**
     * 格式化时间戳为 HH:mm
     */
    private String formatTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(instant,
                java.time.ZoneId.systemDefault());
        return ldt.toLocalTime().toString();
    }

    // ==================== 晚点/停运更新 ====================

    @Override
    public void updateTrainDelay(String trainNumber, String date, int delayMinutes) {
        log.info("[提醒] 更新列车晚点状态: trainNumber={}, date={}, delayMinutes={}",
                trainNumber, date, delayMinutes);

        // 1. 记录列车晚点状态
        String delayKey = CacheConstant.trainDelayKey(trainNumber, date);
        safeCacheTemplate.safeSet(delayKey, delayMinutes, DEFAULT_TTL_DAYS, TimeUnit.DAYS);

        // 2. 查找该车次所有订单的提醒状态并更新
        // TODO: 可以通过 MQ 广播批量更新，或者定时扫描
        log.info("[提醒] 晚点状态已更新，等待下次提醒消息时自动重新调度", trainNumber, date);
    }

    @Override
    public void updateTrainCancel(String trainNumber, String date) {
        log.info("[提醒] 更新列车停运状态: trainNumber={}, date={}", trainNumber, date);

        // 1. 记录停运状态
        String delayKey = CacheConstant.trainDelayKey(trainNumber, date);
        safeCacheTemplate.safeSet(delayKey, -1, DEFAULT_TTL_DAYS, TimeUnit.DAYS); // -1 表示停运

        // 2. TODO: 批量更新该车次所有订单的提醒状态
        log.info("[提醒] 停运状态已更新", trainNumber, date);
    }

    // ==================== 其他方法 ====================

    @Override
    public ReminderState getReminderState(String orderSn) {
        String stateKey = CacheConstant.reminderStateKey(orderSn);
        return safeCacheTemplate.get(stateKey, new TypeReference<ReminderState>() {});
    }

    @Override
    public void sendSms(String phone, String content) {
        // TODO: 调用 user-service 发送短信
        // 这里先简单打印日志
        log.info("[短信] 发送至 {}: {}", phone, content);

        // 实际实现应该调用 user-service 的短信接口
        // userServiceClient.sendSms(phone, content);
    }

    @Override
    public void batchUpdateOrderState(String trainNumber, String date,
                                       Long newPlanDepartTime, Long newPlanArrivalTime,
                                       int newVersion, int status, int delayMinutes) {
        // TODO: 实现批量更新
        // 方案1: 查询该列车该日期的所有订单，逐个更新缓存
        // 方案2: 发送 MQ 消息广播，让各节点更新本地缓存
        // 方案3: 使用 Redis SCAN 命令批量更新

        log.info("[提醒] 批量更新订单状态: trainNumber={}, date={}, newVersion={}, status={}",
                trainNumber, date, newVersion, status);

        // 简化实现：记录到列车晚点缓存，后续消息消费时自动处理
        String delayKey = CacheConstant.trainDelayKey(trainNumber, date);
        if (status == ReminderState.STATUS_CANCEL) {
            safeCacheTemplate.safeSet(delayKey, -1, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
        } else {
            safeCacheTemplate.safeSet(delayKey, delayMinutes, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
        }
    }
}