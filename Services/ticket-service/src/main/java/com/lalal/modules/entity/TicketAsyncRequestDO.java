package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 异步购票请求跟踪实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_ticket_async_request")
public class TicketAsyncRequestDO extends BaseDO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 请求唯一ID
     */
    private String requestId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 车次号
     */
    private String trainNum;

    /**
     * 乘车日期
     */
    private Date date;

    /**
     * 状态：0-处理中，1-成功，2-失败，3-发送失败
     */
    private Integer status;

    /**
     * 成功后的订单号
     */
    private String orderSn;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 原始请求参数JSON（用于重试）
     */
    private String requestParams;
}
