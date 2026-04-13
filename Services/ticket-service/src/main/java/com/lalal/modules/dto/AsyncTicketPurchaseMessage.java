package com.lalal.modules.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 异步购票MQ消息体
 */
@Data
public class AsyncTicketPurchaseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求唯一ID
     */
    private String requestId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 登录账号（手机号）
     */
    private String account;

    /**
     * 车次号
     */
    private String trainNum;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 乘车日期 yyyy-MM-dd
     */
    private LocalDate date;

    /**
     * 乘车人ID列表
     */
    private List<Long> passengerIds;

    /**
     * 座位类型列表
     */
    private List<String> seatTypelist;

    /**
     * 选座偏好
     */
    private List<String> chooseSeats;

    /**
     * 时间戳
     */
    private Long timestamp;
}
