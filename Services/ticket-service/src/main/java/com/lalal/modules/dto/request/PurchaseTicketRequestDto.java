package com.lalal.modules.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseTicketRequestDto {
    /**
     * 购票账号
     */
    String account;
    /**
     * 身份证的索引 不暴露身份证号
     */
    List<Long> IDCardCodelist;
    /**
     * 座位种类
     */
    List<String> seatTypelist;
    /**
     * 座位偏好
     */
    List<String> chooseSeats;
    /**
     * 车次
     */
    String trainNum;
    /**
     * 开始站台
     */
    String startStation;
    /**
     * 到达站台
     */
    String endStation;
    /**
     * 购票日期
     */
    String date;
}
