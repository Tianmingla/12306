package com.lalal.modules.service;

import com.lalal.modules.entity.WaitlistOrderDO;

import java.math.BigDecimal;

/**
 * 候补队列服务接口
 *
 * <p>使用Redis ZSet实现优先级队列：
 * - member: waitlistSn
 * - score: 优先级分数（越大越优先）
 *
 * <p>核心操作：
 * 1. 入队：将候补订单加入队列
 * 2. 出队：ZPOPMAX取出并移除最高优先级订单（兑现时调用）
 * 3. 查看队首：ZRANGE获取但不移除
 */
public interface WaitlistQueueService {

    /**
     * 入队：将候补订单加入优先级队列
     *
     * @param order 候补订单
     * @param priority 优先级分数（越大越优先）
     */
    void enqueue(WaitlistOrderDO order, BigDecimal priority);

    /**
     * 出队：从队列取出并移除最高优先级的候补订单
     *
     * <p>使用ZPOPMAX原子操作，取出分数最高的候补订单
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 候补订单号，未找到返回null
     */
    String dequeue(String trainNumber, String travelDate);

    /**
     * 查看队首：获取最高优先级订单但不移除
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 候补订单号
     */
    String peek(String trainNumber, String travelDate);

    /**
     * 获取队列大小
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 队列中的候补订单数量
     */
    Long size(String trainNumber, String travelDate);

    /**
     * 移除候补订单（取消候补或已兑现时调用）
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     */
    void remove(String waitlistSn, String trainNumber, String travelDate);

    /**
     * 更新优先级分数（失败惩罚时调用）
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param newPriority 新的优先级分数
     */
    void updatePriority(String waitlistSn, String trainNumber, String travelDate, BigDecimal newPriority);

    /**
     * 获取候补订单的优先级分数
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 优先级分数
     */
    Double getScore(String waitlistSn, String trainNumber, String travelDate);

    /**
     * 获取候补订单在队列中的排名
     *
     * @param waitlistSn 候补订单号
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @return 排名（从1开始），不在队列返回null
     */
    Long getQueuePosition(String waitlistSn, String trainNumber, String travelDate);

    /**
     * 获取队列大小（兼容旧方法名）
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param seatType 座位类型（可选，可为null）
     * @return 队列大小
     */
    default Long getQueueSize(String trainNumber, String travelDate, Integer seatType) {
        return size(trainNumber, travelDate);
    }
}
