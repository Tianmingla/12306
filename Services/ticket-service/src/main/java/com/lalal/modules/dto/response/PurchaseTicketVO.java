package com.lalal.modules.dto.response;

import com.lalal.modules.dto.TicketDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseTicketVO {
    String status;
    String orderSn;
    /** 订单应付总额（与 order-service 一致） */
    BigDecimal totalAmount;
    TicketDTO ticketDTO;
}
