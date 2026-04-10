package com.lalal.modules.dto.response;

import com.lalal.modules.dto.TicketDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 异步购票状态查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTicketCheckVO {

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 状态：PROCESSING-处理中, SUCCESS-成功, FAILED-失败
     */
    private String status;

    /**
     * 订单号（成功时返回）
     */
    private String orderSn;

    /**
     * 总金额（成功时返回）
     */
    private BigDecimal totalAmount;

    /**
     * 错误信息（失败时返回）
     */
    private String errorMessage;

    /**
     * 车票信息（成功时返回）
     */
    private TicketDTO ticketDTO;

    public static AsyncTicketCheckVO processing(String requestId) {
        return AsyncTicketCheckVO.builder()
                .requestId(requestId)
                .status("PROCESSING")
                .build();
    }

    public static AsyncTicketCheckVO success(String requestId, String orderSn, BigDecimal totalAmount, TicketDTO ticketDTO) {
        return AsyncTicketCheckVO.builder()
                .requestId(requestId)
                .status("SUCCESS")
                .orderSn(orderSn)
                .totalAmount(totalAmount)
                .ticketDTO(ticketDTO)
                .build();
    }

    public static AsyncTicketCheckVO failed(String requestId, String errorMessage) {
        return AsyncTicketCheckVO.builder()
                .requestId(requestId)
                .status("FAILED")
                .errorMessage(errorMessage)
                .build();
    }
}
