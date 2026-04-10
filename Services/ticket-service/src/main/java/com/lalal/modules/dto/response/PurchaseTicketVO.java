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
    /** 异步请求ID（高峰模式时返回） */
    String requestId;

    /**
     * 创建处理中状态的响应（高峰模式）
     */
    public static PurchaseTicketVO processing(String requestId) {
        PurchaseTicketVO vo = new PurchaseTicketVO();
        vo.setStatus("PROCESSING");
        vo.setRequestId(requestId);
        return vo;
    }

    /**
     * 创建失败状态的响应
     */
    public static PurchaseTicketVO failed(String errorMessage) {
        PurchaseTicketVO vo = new PurchaseTicketVO();
        vo.setStatus("FAILED");
        vo.setRequestId(null);
        return vo;
    }
}
