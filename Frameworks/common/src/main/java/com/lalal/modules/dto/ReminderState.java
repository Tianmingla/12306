package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 提醒状态缓存
 * Redis Key: REMINDER::STATE::{orderSn}
 *
 * 核心设计：
 * - 每次晚点/变更时更新版本号
 * - 消费者校验版本号决定是否发送提醒
 * - 版本不一致时重新计算触发时间并发送新延迟消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderState implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 计划发车时间（时间戳，毫秒）
     * 晚点后更新为新时间
     */
    private Long planDepartTime;

    /**
     * 计划到达时间（时间戳，毫秒）
     */
    private Long planArrivalTime;

    /**
     * 数据版本号
     * 每次变更递增（晚点、停运等）
     */
    private Integer version;

    /**
     *
     * 0: 正常  1: 晚点  2: 停运  3: 检票口变更 4:订单取消
     */
    private Integer status;

    /**
     * 晚点分钟数（status=1时有效）
     */
    private Integer delayMinutes;

    /**
     * 已发送提醒标记（位掩码）
     * bit0: 1h提醒  bit1: 30m提醒  bit2: 到达提醒
     */
    private Integer sentFlags;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 乘车日期
     */
    private String runDate;

    /**
     * 用户名（手机号）
     */
    private String username;

    // 状态常量
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_DELAY = 1;
    public static final int STATUS_CANCEL = 2;
    public static final int STATUS_GATE_CHANGE = 3;
    public static final int STATUS_ORDER_CANCEL = 4;

    // 提醒发送标记
    public static final int FLAG_1H_SENT = 1;      // bit0
    public static final int FLAG_30M_SENT = 2;     // bit1
    public static final int FLAG_ARRIVAL_SENT = 4; // bit2

    /**
     * 标记已发送某类型提醒
     */
    public void markSent(int flag) {
        if (sentFlags == null) {
            sentFlags = 0;
        }
        sentFlags |= flag;
    }

    /**
     * 检查是否已发送某类型提醒
     */
    public boolean hasSent(int flag) {
        return sentFlags != null && (sentFlags & flag) != 0;
    }
}