package com.lalal.modules.controller;


import com.lalal.framework.idempotent.Idempotent;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticket")
public class PurchaseTicketController {
    @Autowired
    TicketService ticketService;

    /**
     * 网关会在已登录请求上注入 X-User-Id，供与 user-service 核对乘车人。
     * 幂等性保护：同一用户重复提交购票请求会被拦截
     */
    @Idempotent(
        key = "${header.X-User-Id}-${#purchaseTicketRequestDto.trainNum}-${#purchaseTicketRequestDto.date}",
        expire = 300,
        message = "购票请求正在处理中，请勿重复提交",
        cacheResult = true
    )
    @PostMapping("/purchase")
    public Result<PurchaseTicketVO> purchaseTicket(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestBody PurchaseTicketRequestDto purchaseTicketRequestDto) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return Result.fail("请先登录后再购票", ReturnCode.fail.code());
        }
        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return Result.fail("用户身份无效",ReturnCode.fail.code());
        }
        return Result.success(ticketService.purchase(purchaseTicketRequestDto, userId));
    }

    /**
     * 查询异步购票请求状态（高峰模式）
     * 前端轮询此接口获取购票处理结果
     */
    @GetMapping("/check/{requestId}")
    public Result<AsyncTicketCheckVO> checkStatus(
            @PathVariable("requestId") String requestId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return Result.fail("请先登录", ReturnCode.fail.code());
        }
        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return Result.fail("用户身份无效", ReturnCode.fail.code());
        }
        return Result.success(ticketService.check(requestId, userId));
    }

}
