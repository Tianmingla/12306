package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 选座结果消息
 * seat-service 发送到 ticket-service
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeatSelectionResultMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID
     */
    private String requestId;

    /**
     * 候补订单号（候补订单选座时使用）
     */
    private String waitlistSn;

    /**
     * 选座是否成功
     */
    private boolean success;

    /**
     * 选中的座位列表
     */
    private List<SeatItem> selectedSeats;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 座位项
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SeatItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 乘车人ID
         */
        private Long passengerId;

        /**
         * 车厢号
         */
        private String carriageNum;

        /**
         * 座位号
         */
        private String seatNum;

        /**
         * 座位类型
         */
        private Integer seatType;
    }
}
