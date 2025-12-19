package com.lalal.modules.dto.response;

import java.util.List;

public class TicketVO {
    /**
     * 账号
     */
    String account;
    /**
     * 票的主人
     */
    List<String> namelist;
    /**
     * 车次号
     */
    String trainNum;
    /**
     * 座位号
     */
    List<String> seatNumlist;
    /**
     * 座位种类
     */
    List<String> seatTypelist;
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
}
