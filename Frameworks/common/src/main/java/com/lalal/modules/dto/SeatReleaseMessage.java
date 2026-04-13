package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 座位释放消息DTO
 * 用于订单取消/退票/超时时释放座位
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeatReleaseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID
     */
    private String requestId;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 车次号
     */
    private String trainNum;

    /**
     * 运行日期
     */
    private LocalDate date;

    /**
     * 释放原因
     */
    private String reason;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 需要释放的座位列表
     */
    private List<SeatItem> seats;

    /**
     * 释放类型: CANCEL(取消), REFUND(退票), TIMEOUT(超时)
     */
    private ReleaseType releaseType;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SeatItem implements Serializable {
        /**
         * 车厢号
         */
        private String carriageNumber;

        /**
         * 座位号
         */
        private String seatNumber;

        /**
         * 座位类型
         */
        private Integer seatType;
    }

    public enum ReleaseType {
        CANCEL,   // 用户主动取消
        REFUND,   // 退票
        TIMEOUT   // 超时自动取消
    }
}
