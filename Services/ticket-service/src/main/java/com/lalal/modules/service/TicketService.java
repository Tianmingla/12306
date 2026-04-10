package com.lalal.modules.service;


import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;
import com.lalal.modules.dto.response.PurchaseTicketVO;

import java.util.List;


public interface TicketService{
    PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto, Long userId);

    /**
     * 查询异步购票状态
     */
    AsyncTicketCheckVO check(String requestId, Long userId);

    /**
     * 核心购票逻辑（同步处理）
     */
    PurchaseTicketVO processCorePurchase(Long userId, String trainNum, String startStation,
                                         String endStation, String date, List<Long> passengerIds, List<String> seatTypelist,
                                         List<String> chooseSeats, String account);

}
