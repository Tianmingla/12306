package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 选座请求消息
 * ticket-service 发送到 seat-service
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeatSelectionRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID，用于链路追踪
     */
    private String requestId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 购票账号(手机号)
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
    private String date;

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
