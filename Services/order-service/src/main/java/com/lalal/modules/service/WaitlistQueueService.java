package com.lalal.modules.service;

import com.lalal.modules.entity.WaitlistOrderDO;
import java.math.BigDecimal;

/**
 * 候补队列服务接口
 */
public interface WaitlistQueueService {

    /**
     * 候补订单入队
     *
     * @param order 候补订单
     * @param priority 优先级分数（越大越优先）
     */
    void enqueue(WaitlistOrderDO order, BigDecimal priority);

    /**
     * 候补订单出队（获取优先级最高的）
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param seatType 座位类型（null表示所有类型）
     * @return 候补订单号，无则返回 null
     */
    String dequeue(String trainNumber, String travelDate, Integer seatType);

    /**
     * 获取队列位置（排名）
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 位置（从1开始），无则返回 null
     */
    Long getQueuePosition(String waitlistSn, String trainNumber, String travelDate);

    /**
     * 从队列移除候补订单
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     */
    void remove(String waitlistSn, String trainNumber, String travelDate);

    /**
     * 更新优先级分数
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param newPriority 新优先级
     */
    void updatePriority(String waitlistSn, String trainNumber, String travelDate, BigDecimal newPriority);

    /**
     * 获取队列长度
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param seatType 座位类型
     * @return 队列长度
     */
    Long getQueueSize(String trainNumber, String travelDate, Integer seatType);
}
