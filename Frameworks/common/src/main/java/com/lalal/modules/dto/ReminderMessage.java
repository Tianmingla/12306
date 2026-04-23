package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 出行提醒消息体
 * 订单创建成功后发送到 RocketMQ 延迟队列
 *
 * 核心设计：版本控制 + 动态调度
 * - 消息携带版本号
 * - 消费时校验版本，不一致则重新调度
 * - 晚点时更新缓存状态，后续消息自动修正
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 唯一请求ID
     */
    private String requestId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 乘车日期
     */
    private String runDate;

    /**
     * 出发站
     */
    private String departureStation;

    /**
     * 到达站
     */
    private String arrivalStation;

    /**
     * 用户名（手机号）
     */
    private String username;

    /**
     * 乘客姓名
     */
    private String passengerName;

    /**
     * 计划发车时间（时间戳，毫秒）
     */
    private Long planDepartTime;

    /**
     * 计划到达时间（时间戳，毫秒）
     */
    private Long planArrivalTime;

    /**
     * 数据版本号
     * 每次晚点/变更时递增
     */
    private Integer version;

    /**
     * 提醒类型
     * 1: 发车前1小时  2: 发车前30分钟  3: 到达提醒  4: 晚点通知
     */
    private Integer reminderType;

    /**
     * 提醒触发时间（时间戳，毫秒）
     */
    private Long triggerTime;

    /**
     * 备注（如晚点分钟数）
     */
    private String remark;

    // 提醒类型常量
    public static final int TYPE_BEFORE_DEPART_1H = 1;
    public static final int TYPE_BEFORE_DEPART_30M = 2;
    public static final int TYPE_ARRIVAL = 3;
    public static final int TYPE_DELAY_NOTICE = 4;
}