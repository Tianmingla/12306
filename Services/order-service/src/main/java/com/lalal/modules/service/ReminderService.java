package com.lalal.modules.service;

import com.lalal.modules.dto.ReminderMessage;
import com.lalal.modules.dto.ReminderState;

/**
 * 出行提醒服务
 *
 * 核心功能：
 * 1. 订单创建时初始化提醒状态并发送延迟消息
 * 2. 消费延迟消息时校验版本号
 * 3. 晚点时更新状态并重新调度提醒
 */
public interface ReminderService {

    /**
     * 初始化提醒状态并发送延迟消息
     *
     * @param orderSn 订单号
     * @param trainNumber 车次号
     * @param runDate 乘车日期
     * @param departureStation 出发站
     * @param arrivalStation 到达站
     * @param username 用户名（手机号）
     * @param passengerName 乘客姓名
     * @param planDepartTime 计划发车时间（时间戳）
     * @param planArrivalTime 计划到达时间（时间戳）
     */
    void initReminderState(String orderSn, String trainNumber, String runDate,
                          String departureStation, String arrivalStation,
                          String username, String passengerName,
                          Long planDepartTime, Long planArrivalTime);

    /**
     * 处理提醒消息（消费延迟消息）
     *
     * @param message 提醒消息
     */
    void processReminder(ReminderMessage message);

    /**
     * 更新列车晚点状态
     *
     * @param trainNumber 车次号
     * @param date 乘车日期
     * @param delayMinutes 晚点分钟数
     */
    void updateTrainDelay(String trainNumber, String date, int delayMinutes);

    /**
     * 更新列车停运状态
     *
     * @param trainNumber 车次号
     * @param date 乘车日期
     */
    void updateTrainCancel(String trainNumber, String date);

    /**
     * 获取提醒状态
     *
     * @param orderSn 订单号
     * @return 提醒状态
     */
    ReminderState getReminderState(String orderSn);

    /**
     * 发送短信（调用 user-service）
     *
     * @param phone 手机号
     * @param content 短信内容
     */
    void sendSms(String phone, String content);

    /**
     * 批量更新订单提醒状态（晚点/停运时调用）
     *
     * @param trainNumber 车次号
     * @param date 乘车日期
     * @param newPlanDepartTime 新的计划发车时间（毫秒时间戳）
     * @param newPlanArrivalTime 新的计划到达时间
     * @param newVersion 新版本号
     * @param status 新状态
     * @param delayMinutes 晚点分钟数
     */
    void batchUpdateOrderState(String trainNumber, String date,
                               Long newPlanDepartTime, Long newPlanArrivalTime,
                               int newVersion, int status, int delayMinutes);
}