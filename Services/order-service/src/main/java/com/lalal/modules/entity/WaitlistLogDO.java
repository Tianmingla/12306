package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 候补订单操作日志实体
 *
 * <p>记录候补订单的关键操作，用于追踪和审计
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_waitlist_log")
public class WaitlistLogDO extends BaseDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 候补订单号
     */
    private String waitlistSn;

    /**
     * 操作动作
     * CREATE - 创建
     * CHECK - 检查余票
     * SEAT_SELECT - 选座
     * ORDER_CREATE - 订单创建
     * SUCCESS - 成功
     * FAIL - 失败
     * CANCEL - 取消
     * EXPIRE - 过期
     * PRIORITY_RECALC - 优先级重算
     */
    private String action;

    /**
     * 操作前状态
     */
    private Integer statusBefore;

    /**
     * 操作后状态
     */
    private Integer statusAfter;

    /**
     * 操作描述/备注
     */
    private String message;

    /**
     * 关联的MQ消息ID（幂等性追踪）
     */
    private String messageId;

    /**
     * 操作结果：0-失败 1-成功
     */
    private Integer success;
}
