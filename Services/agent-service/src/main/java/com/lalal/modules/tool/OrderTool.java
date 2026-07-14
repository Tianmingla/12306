package com.lalal.modules.tool;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.dto.FeignResult;
import com.lalal.modules.feign.OrderFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 订单管理工具
 * 提供订单查询、退票、取消订单功能
 * 敏感操作（退票/取消）需要用户确认（Human-in-the-Loop）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTool {

    private final OrderFeignClient orderFeignClient;
    private final ToolRegistry toolRegistry;

    @jakarta.annotation.PostConstruct
    public void init() {
        toolRegistry.register(this);
    }

    @Tool(description = "查询订单详情。根据订单号查询订单的详细信息，包括车次、出发/到达站、乘车日期、金额、状态、乘车人信息等。")
    public String queryOrderDetail(
            @ToolParam(description = "订单号，如：ORD20260715001") String orderSn,
            ToolContext toolContext) {
        log.info("Tool: queryOrderDetail orderSn={}", orderSn);

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        if (orderSn == null || orderSn.isBlank()) {
            return "请提供订单号";
        }

        try {
            FeignResult result = orderFeignClient.getOrderDetail(orderSn, user.getUserName());
            if (result.isSuccess()) {
                return JSON.toJSONString(result.getData());
            } else {
                return "查询订单失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("queryOrderDetail error", e);
            return "查询订单时发生错误";
        }
    }

    @Tool(description = "查询我的所有订单列表。返回当前用户的所有订单，包括订单号、车次、路线、金额、状态等。")
    public String queryMyOrders(ToolContext toolContext) {
        log.info("Tool: queryMyOrders");

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        try {
            FeignResult result = orderFeignClient.getOrderList(user.getUserName());
            if (result.isSuccess()) {
                return JSON.toJSONString(result.getData());
            } else {
                return "查询订单列表失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("queryMyOrders error", e);
            return "查询订单列表时发生错误";
        }
    }

    @Tool(description = "退票操作。根据订单号办理退票，退票后将按退票规则收取手续费。此操作不可撤销，请确认后再执行。")
    public String refundTicket(
            @ToolParam(description = "要退票的订单号") String orderSn,
            ToolContext toolContext) {
        log.info("Tool: refundTicket orderSn={}", orderSn);

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        if (orderSn == null || orderSn.isBlank()) {
            return "请提供要退票的订单号";
        }

        try {
            FeignResult result = orderFeignClient.refundOrder(orderSn, user.getUserName());
            if (result.isSuccess()) {
                return "退票成功！订单号：" + orderSn + "，退款将按退票规则处理。";
            } else {
                return "退票失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("refundTicket error", e);
            return "退票操作发生错误，请稍后重试";
        }
    }

    @Tool(description = "取消订单。取消未支付的待支付订单，已支付的订单请使用退票操作。此操作不可撤销。")
    public String cancelOrder(
            @ToolParam(description = "要取消的订单号") String orderSn,
            ToolContext toolContext) {
        log.info("Tool: cancelOrder orderSn={}", orderSn);

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        if (orderSn == null || orderSn.isBlank()) {
            return "请提供要取消的订单号";
        }

        try {
            FeignResult result = orderFeignClient.cancelOrder(orderSn, user.getUserName());
            if (result.isSuccess()) {
                return "订单取消成功！订单号：" + orderSn;
            } else {
                return "取消订单失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("cancelOrder error", e);
            return "取消订单操作发生错误，请稍后重试";
        }
    }

    @Tool(description = "查询候补订单列表。返回当前用户的所有候补订单，包括候补状态、排队位置、预计成功率等。")
    public String queryWaitlistOrders(ToolContext toolContext) {
        log.info("Tool: queryWaitlistOrders");

        UserContext user = ToolContextHelper.getUserContext(toolContext);
        user.requireAuthenticated();

        try {
            FeignResult result = orderFeignClient.getWaitlistList(user.getUserName());
            if (result.isSuccess()) {
                return JSON.toJSONString(result.getData());
            } else {
                return "查询候补订单失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("queryWaitlistOrders error", e);
            return "查询候补订单时发生错误";
        }
    }
}
