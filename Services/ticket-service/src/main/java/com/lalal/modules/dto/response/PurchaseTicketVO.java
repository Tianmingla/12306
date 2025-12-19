package com.lalal.modules.dto.response;

import com.lalal.modules.entity.TicketDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseTicketVO {
    String status;
    TicketDO ticketDO;
}
