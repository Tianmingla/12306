package com.lalal.modules.service.impl;

import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.config.AlipayProperties;
import com.lalal.modules.dto.OrderCreationResultMessage;
import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.dto.response.OrderDetailVO;
import com.lalal.modules.dto.response.OrderItemVO;
import com.lalal.modules.dto.response.OrderListVO;
import com.lalal.modules.dto.response.PayOrderVO;
import com.lalal.modules.entity.OrderDO;
import com.lalal.modules.entity.OrderItemDO;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.OrderItemMapper;
import com.lalal.modules.mapper.OrderMapper;
import com.lalal.modules.service.AlipayTradeService;
import com.lalal.modules.service.OrderService;
import com.lalal.modules.dto.SeatReleaseMessage;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderDO> implements OrderService {

    private static final String ORDER_TIMEOUT_CANCEL_TOPIC ="order-timeout-cancel-topic";
    private final OrderItemMapper orderItemMapper;
    private final AlipayTradeService alipayTradeService;
    private final AlipayProperties alipayProperties;
    private final MessageQueueService messageQueueService;
    private final ReminderService reminderService;

    private static final String SEAT_RELEASE_TOPIC = "seat-release-topic";

    /**
     * 发送座位释放消息
     */
    private void sendSeatReleaseMessage(OrderDO order, SeatReleaseMessage.ReleaseType releaseType) {
        // 查询订单项获取座位信息
        LambdaQueryWrapper<OrderItemDO> itemQw = new LambdaQueryWrapper<>();
        itemQw.eq(OrderItemDO::getOrderSn, order.getOrderSn());
        List<OrderItemDO> orderItems = orderItemMapper.selectList(itemQw);

        if (orderItems.isEmpty()) {
            return;
        }

        // 构建座位列表
        List<SeatReleaseMessage.SeatItem> seats = orderItems.stream()
                .map(item -> new SeatReleaseMessage.SeatItem(
                        item.getCarriageNumber(),
                        item.getSeatNumber(),
                        item.getSeatType()
                ))
                .collect(Collectors.toList());


        SeatReleaseMessage message = new SeatReleaseMessage();
        message.setOrderSn(order.getOrderSn());
        message.setTrainNum(order.getTrainNumber());
        message.setDate(order.getRunDate());
        message.setStartStation(order.getStartStation());
        message.setEndStation(order.getEndStation());
        message.setSeats(seats);
        message.setReleaseType(releaseType);

        // 发送消息
        messageQueueService.send(SEAT_RELEASE_TOPIC, releaseType.name().toLowerCase(), message);
    }

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
        // 初始化出行提醒（延迟消息 + 版本控制） 目前只能提醒一个
        reminderService.initReminderState(
                orderSn, request.getTrainNumber(), DateTimeFormatter.ofPattern("yyyy-MM-dd").format(request.getRunDate()),
                request.getStartStation(), request.getEndStation(),
                request.getUsername(),request.getItems().get(0).getRealName(),
                LocalDateTime.now().atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(), LocalDateTime.now().atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
        );
        OrderCreationResultMessage msg=new OrderCreationResultMessage();
        msg.setOrderSn(orderSn);
        //发送超时取消延迟消息
        messageQueueService.sendDelay(ORDER_TIMEOUT_CANCEL_TOPIC,msg,30*60*1000);
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
                order.setPayTime(LocalDateTime.now());
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
        v.setSeatType(SeatType.getDescByCode(d.getSeatType()));
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

    @Override
    public List<OrderListVO> getOrderList(String phone) {
        if (!StringUtils.hasText(phone)) {
            return List.of();
        }
        LambdaQueryWrapper<OrderDO> qw = new LambdaQueryWrapper<>();
        qw.eq(OrderDO::getUsername, phone);
        qw.orderByDesc(OrderDO::getCreateTime);
        List<OrderDO> orders = this.list(qw);

        if (orders.isEmpty()) {
            return List.of();
        }

        // 批量查询乘客数量（优化 N+1 查询）
        List<String> orderSns = orders.stream()
                .map(OrderDO::getOrderSn)
                .collect(Collectors.toList());
        Map<String, Integer> passengerCountMap = batchGetPassengerCounts(orderSns);

        return orders.stream().map(order -> {
            OrderListVO vo = new OrderListVO();
            vo.setOrderSn(order.getOrderSn());
            vo.setTrainNumber(order.getTrainNumber());
            vo.setStartStation(order.getStartStation());
            vo.setEndStation(order.getEndStation());
            vo.setRunDate(order.getRunDate());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusText(statusText(order.getStatus()));
            vo.setPassengerCount(passengerCountMap.getOrDefault(order.getOrderSn(), 0));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 批量查询订单的乘客数量
     */
    private Map<String, Integer> batchGetPassengerCounts(List<String> orderSns) {
        if (orderSns == null || orderSns.isEmpty()) {
            return Map.of();
        }

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderItemDO> wrapper =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        wrapper.select("order_sn", "COUNT(*) as count")
               .in("order_sn", orderSns)
               .groupBy("order_sn");

        List<Map<String, Object>> results = orderItemMapper.selectMaps(wrapper);

        return results.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("order_sn"),
                        m -> ((Number) m.get("count")).intValue()
                ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderSn, String phone) {
        OrderDO order = findByOrderSn(orderSn);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(phone) || !phone.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        if (!Objects.equals(order.getStatus(), 1)) {
            throw new IllegalStateException("只有已支付的订单才能退款");
        }
        // 检查是否已发车
        if (order.getRunDate() != null) {
            if (order.getRunDate().isBefore(LocalDate.now())) {
                throw new IllegalStateException("列车已发车，无法退款");
            }
        }

        // 如果支付宝功能启用，调用退款接口
        if (alipayProperties.isEnabled() && order.getTotalAmount() != null) {
            try {
                alipayTradeService.refund(orderSn, order.getTotalAmount());
            } catch (AlipayApiException e) {
                throw new IllegalStateException("支付宝退款失败: " + e.getMessage());
            }
        }

        order.setStatus(3); // 已退票
        this.updateById(order);

        // 发送座位释放消息
        sendSeatReleaseMessage(order, SeatReleaseMessage.ReleaseType.REFUND);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderSn,String phone) {
        OrderDO order = findByOrderSn(orderSn);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!StringUtils.hasText(phone) || !phone.equals(order.getUsername())) {
            throw new IllegalArgumentException("无权操作该订单");
        }
        if (!Objects.equals(order.getStatus(), 0)) {
            throw new IllegalStateException("只有待支付的订单才能取消");
        }

        order.setStatus(2); // 已取消
        this.updateById(order);

        // 发送座位释放消息
        sendSeatReleaseMessage(order, SeatReleaseMessage.ReleaseType.CANCEL);
        reminderService.handleOrderCancel(orderSn);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderSn) {
        OrderDO order = findByOrderSn(orderSn);
        order.setStatus(2); // 已取消
        this.updateById(order);
        // 发送座位释放消息
        sendSeatReleaseMessage(order, SeatReleaseMessage.ReleaseType.CANCEL);
        reminderService.handleOrderCancel(orderSn);
    }
}
