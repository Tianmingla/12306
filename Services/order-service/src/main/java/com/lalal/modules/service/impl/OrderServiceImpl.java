package com.lalal.modules.service.impl;

import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.config.AlipayProperties;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.request.OrderPayRequest;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.OrderItemVO;
import com.lalal.modules.dto.response.PayOrderVO;
import com.lalal.modules.entity.OrderDO;
import com.lalal.modules.entity.OrderItemDO;
import com.lalal.modules.mapper.OrderItemMapper;
import com.lalal.modules.mapper.OrderMapper;
import com.lalal.modules.service.AlipayTradeService;
import com.lalal.modules.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderDO> implements OrderService {

    private final OrderItemMapper orderItemMapper;
    private final AlipayTradeService alipayTradeService;
    private final AlipayProperties alipayProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateRequestDTO request) {
        String orderSn = UUID.randomUUID().toString().replace("-", "");

        BigDecimal total = BigDecimal.ZERO;
        List<OrderCreateRequestDTO.OrderItemRequestDTO> items = request.getItems();
        if (items == null) {
            items = List.of();
        }
        for (OrderCreateRequestDTO.OrderItemRequestDTO item : items) {
            if (item.getAmount() != null) {
                total = total.add(item.getAmount());
            }
        }

        OrderDO orderDO = new OrderDO();
        orderDO.setOrderSn(orderSn);
        orderDO.setTrainNumber(request.getTrainNumber());
        orderDO.setStartStation(request.getStartStation());
        orderDO.setEndStation(request.getEndStation());
        orderDO.setStatus(0);
        orderDO.setUsername(request.getUsername());
        orderDO.setRunDate(request.getRunDate());
        orderDO.setTotalAmount(total);
        this.save(orderDO);

        for (OrderCreateRequestDTO.OrderItemRequestDTO item : items) {
            OrderItemDO itemDO = new OrderItemDO();
            itemDO.setOrderId(orderDO.getId());
            itemDO.setOrderSn(orderSn);
            itemDO.setCarriageNumber(item.getCarriageNumber());
            itemDO.setSeatNumber(item.getSeatNumber());
            itemDO.setSeatType(item.getSeatType());
            itemDO.setAmount(item.getAmount());
            itemDO.setIdCard(item.getIdCard());
            itemDO.setPassengerId(item.getPassengerId());
            itemDO.setPassengerName(item.getRealName());
            orderItemMapper.insert(itemDO);
        }

        return orderSn;
    }

    @Override
    public OrderDetailVO getOrderDetail(String orderSn, String phone) {
        OrderDO order = findByOrderSn(orderSn);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(phone) || !phone.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权查看该订单");
        }

        LambdaQueryWrapper<OrderItemDO> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderItemDO::getOrderSn, orderSn);
        List<OrderItemDO> rows = orderItemMapper.selectList(qw);

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderSn(order.getOrderSn());
        vo.setUsername(order.getUsername());
        vo.setTrainNumber(order.getTrainNumber());
        vo.setStartStation(order.getStartStation());
        vo.setEndStation(order.getEndStation());
        vo.setRunDate(order.getRunDate());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setItems(rows.stream().map(this::toItemVo).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public PayOrderVO createPayForm(String orderSn, String phone) {
        OrderDO order = findByOrderSn(orderSn);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(phone) || !phone.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权支付该订单");
        }
        if (order.getStatus() != null && order.getStatus() != 0) {
            throw new IllegalStateException("订单当前状态不可支付");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("订单金额无效");
        }

        if (!alipayProperties.isEnabled()) {
            return new PayOrderVO(orderSn, null, "请在 order-service 的 application.yml 中配置 alipay.enabled=true 及沙箱密钥");
        }

        try {
            String form = alipayTradeService.buildPagePayForm(orderSn, order.getTotalAmount(), "火车票订单-" + orderSn);
            return new PayOrderVO(orderSn, form, null);
        } catch (AlipayApiException e) {
            return new PayOrderVO(orderSn, null, "调起支付宝失败: " + e.getMessage());
        }
    }

    @Override
    public String handleAlipayNotify(Map<String, String> params) {
        if (!alipayProperties.isEnabled()) {
            return "success";
        }
        try {
            if (!alipayTradeService.verifyNotify(params)) {
                return "fail";
            }
        } catch (AlipayApiException e) {
            return "fail";
        }
        String tradeStatus = params.get("trade_status");
        String outTradeNo = params.get("out_trade_no");
        if (!StringUtils.hasText(outTradeNo)) {
            return "fail";
        }
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            OrderDO order = findByOrderSn(outTradeNo);
            if (order != null && Objects.equals(order.getStatus(), 0)) {
                order.setStatus(1);
                this.updateById(order);
            }
        }
        return "success";
    }

    private OrderDO findByOrderSn(String orderSn) {
        LambdaQueryWrapper<OrderDO> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderDO::getOrderSn, orderSn);
        return this.getOne(qw);
    }

    private OrderItemVO toItemVo(OrderItemDO d) {
        OrderItemVO v = new OrderItemVO();
        v.setId(d.getId());
        v.setPassengerId(d.getPassengerId());
        v.setPassengerName(d.getPassengerName());
        v.setIdCardMasked(maskIdCard(d.getIdCard()));
        v.setCarriageNumber(d.getCarriageNumber());
        v.setSeatNumber(d.getSeatNumber());
        v.setSeatType(d.getSeatType());
        v.setAmount(d.getAmount());
        return v;
    }

    private static String maskIdCard(String id) {
        if (!StringUtils.hasText(id) || id.length() < 8) {
            return id == null ? "" : id;
        }
        return id.substring(0, 4) + "********" + id.substring(id.length() - 4);
    }

    private static String statusText(Integer s) {
        if (s == null) return "未知";
        return switch (s) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已退票";
            default -> "未知";
        };
    }
}
