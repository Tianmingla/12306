package com.lalal.modules.constant.cache;

/**
 * 候补订单相关缓存常量
 */
public final class WaitlistCacheConstant {

    private WaitlistCacheConstant() {}

    /**
     * 候补队列 Sorted Set Key
     * 格式：WAITLIST:QUEUE::{trainNumber}::{travelDate}::{seatType}
     * score = 优先级分数，value = waitlistSn
     */
    public static String waitlistQueueKey(String trainNumber, String travelDate, Integer seatType) {
        if (seatType == null) {
            return String.format("WAITLIST:QUEUE::%s::%s::ALL", trainNumber, travelDate);
        }
        return String.format("WAITLIST:QUEUE::%s::%s::%d", trainNumber, travelDate, seatType);
    }

    /**
     * 候补订单详情 Hash Key
     * 格式：WAITLIST:DETAIL::{waitlistSn}
     */
    public static String waitlistDetailKey(String waitlistSn) {
        return "WAITLIST:DETAIL::" + waitlistSn;
    }

    /**
     * 候补订单分布式锁 Key
     * 格式：WAITLIST:LOCK::{waitlistSn}
     */
    public static String waitlistLockKey(String waitlistSn) {
        return "WAITLIST:LOCK::" + waitlistSn;
    }

    /**
     * 候补订单消息ID去重 Set Key
     * 格式：WAITLIST:MSGID::{requestId}
     */
    public static String waitlistMessageIdKey(String requestId) {
        return "WAITLIST:MSGID::" + requestId;
    }

    /**
     * 候补订单队列位置缓存 Key
     * 格式：WAITLIST:QUEUE_POS::{waitlistSn}
     */
    public static String waitlistQueuePositionKey(String waitlistSn) {
        return "WAITLIST:QUEUE_POS::" + waitlistSn;
    }

    /**
     * 候补订单成功率统计 Hash
     * 格式：WAITLIST:STATS::{trainNumber}::{travelDate}
     * fields: total, fulfilled, rate
     */
    public static String waitlistStatsKey(String trainNumber, String travelDate) {
        return String.format("WAITLIST:STATS::%s::%s", trainNumber, travelDate);
    }

    /**
     * 候补订单操作日志 List Key
     * 格式：WAITLIST:LOG::{waitlistSn}
     */
    public static String waitlistLogKey(String waitlistSn) {
        return "WAITLIST:LOG::" + waitlistSn;
    }

    /**
     * 车次候补统计 Hash
     * 格式：WAITLIST:TRAIN_STATS::{trainNumber}::{travelDate}
     */
    public static String waitlistTrainStatsKey(String trainNumber, String travelDate) {
        return String.format("WAITLIST:TRAIN_STATS::%s::%s", trainNumber, travelDate);
    }
}
