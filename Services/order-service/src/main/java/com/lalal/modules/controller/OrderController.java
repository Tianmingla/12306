package com.lalal.modules.controller;

import com.alipay.api.AlipayApiException;
import com.lalal.framework.idempotent.Idempotent;
import com.lalal.modules.config.AlipayProperties;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.request.OrderPayRequest;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.OrderListVO;
import com.lalal.modules.dto.response.PayOrderVO;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.AlipayTradeService;
import com.lalal.modules.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AlipayProperties alipayProperties;
    private final AlipayTradeService alipayTradeService;

    @Idempotent(
        key = "${#request.trainNumber}-${#request.startStation}-${#request.endStation}-${#request.username}",
        expire = 300,
        message = "订单创建请求正在处理中，请勿重复提交",
        cacheResult = true
    )
    @PostMapping("/create")
    public String create(@RequestBody OrderCreateRequestDTO request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/detail/{orderSn}")
    public Result<OrderDetailVO> detail(
            @PathVariable String orderSn,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {
        try {
            return Result.success(orderService.getOrderDetail(orderSn, phone));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 获取用户历史订单列表
     */
    @GetMapping("/list")
    public Result<List<OrderListVO>> list(
            @RequestHeader(value = "X-User-Name", required = false) String phone) {
        return Result.success(orderService.getOrderList(phone));
    }

    /**
     * 退款接口（仅限已支付且未发车的订单）
     */
    @PostMapping("/refund/{orderSn}")
    public Result<Void> refund(
            @PathVariable String orderSn,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {
        try {
            orderService.refundOrder(orderSn, phone);
            return Result.success(null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 取消订单接口（仅限待支付订单）
     */
    @PostMapping("/cancel/{orderSn}")
    public Result<Void> cancel(
            @PathVariable String orderSn,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {
        try {
            orderService.cancelOrder(orderSn, phone);
            return Result.success(null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    @PostMapping("/pay")
    public Result<PayOrderVO> pay(
            @RequestHeader(value = "X-User-Name", required = false) String phone,
            @RequestBody OrderPayRequest body) {
        if (body == null || body.getOrderSn() == null || body.getOrderSn().isBlank()) {
            return Result.fail("orderSn 不能为空",ReturnCode.fail.code());
        }
        try {
            PayOrderVO vo = orderService.createPayForm(body.getOrderSn(), phone);
            if (vo.getPayFormHtml() == null && vo.getHint() != null) {
                return Result.fail(vo.getHint(),ReturnCode.fail.code());
            }
            return Result.success(vo);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage(),ReturnCode.fail.code());
        }
    }

    /**
     * 支付回调接口 - 添加幂等性保护
     * 防止支付宝重复回调导致订单状态异常
     * 使用 out_trade_no（商户订单号）作为幂等键
     */
    @Idempotent(
        key = "${#request.getParameter('out_trade_no')}",
        expire = 600,
        message = "支付回调正在处理中",
        cacheResult = true,
        deleteKeyOnSuccess = false
    )
    @PostMapping(value = "/pay/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = toSingleValueMap(request);
        return orderService.handleAlipayNotify(params);
    }

    @GetMapping("/pay/return")
    public RedirectView alipayReturn(HttpServletRequest request) {
        Map<String, String> params = toSingleValueMap(request);
        String base = alipayProperties.getFrontendBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            if (alipayProperties.isEnabled() && !alipayTradeService.verifyReturn(params)) {
                return new RedirectView(base + "/?payError=sign");
            }
        } catch (AlipayApiException e) {
            return new RedirectView(base + "/?payError=verify");
        }
        String orderSn = params.get("out_trade_no");
        return new RedirectView(base + "/?paid=1&orderSn=" + (orderSn != null ? orderSn : ""));
    }

    private static Map<String, String> toSingleValueMap(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                map.put(k, v[0]);
            }
        });
        return map;
    }
}
