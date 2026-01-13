package com.lalal.modules.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class TicketDTO {
    /**
     * 账号
     */
    String account;
    /**
     * 车次号
     */
    String trainNum;

    List<TicketItem> items;
    /**
     * 开始站台
     */
    String startStation;
    /**
     * 结束站台
     */
    String endStation;
    /**
     * 票的日期
     */
    String date;
    @Data
    @AllArgsConstructor
    public static class TicketItem {
        private long passengerId;
        private String seatNum;      // 物理座位号，如 "12F"
        private Integer seatType;
        private String carriageNum;
    }
}

