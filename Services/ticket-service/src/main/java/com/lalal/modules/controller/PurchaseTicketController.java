package com.lalal.modules.controller;


import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.TicketService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller("/api/ticket")
public class PurchaseTicketController {
    TicketService ticketService;
    @PostMapping("/purchase")
    public Result<PurchaseTicketVO> purchaseTicket(@RequestBody PurchaseTicketRequestDto purchaseTicketRequestDto){
        return Result.success(ticketService.purchase(purchaseTicketRequestDto));
    }

}
