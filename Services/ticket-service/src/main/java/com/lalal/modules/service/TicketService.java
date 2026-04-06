package com.lalal.modules.service;


import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;


public interface TicketService{
    PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto, Long userId);
    PurchaseTicketVO check(String RequestId);

}
