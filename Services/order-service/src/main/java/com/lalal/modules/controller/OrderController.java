package com.lalal.modules.controller;

import com.alipay.api.AlipayApiException;
import com.lalal.modules.config.AlipayProperties;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.request.OrderPayRequest;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.PayOrderVO;
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
import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AlipayProperties alipayProperties;
    private final AlipayTradeService alipayTradeService;

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
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/pay")
    public Result<PayOrderVO> pay(
            @RequestHeader(value = "X-User-Name", required = false) String phone,
            @RequestBody OrderPayRequest body) {
        if (body == null || body.getOrderSn() == null || body.getOrderSn().isBlank()) {
            return Result.fail("orderSn 不能为空");
        }
        try {
            PayOrderVO vo = orderService.createPayForm(body.getOrderSn(), phone);
            if (vo.getPayFormHtml() == null && vo.getHint() != null) {
                return Result.fail(vo.getHint());
            }
            return Result.success(vo);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

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
