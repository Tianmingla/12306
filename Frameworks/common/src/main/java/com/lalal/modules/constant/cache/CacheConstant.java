package com.lalal.modules.constant.cache;

import java.util.Objects;

public class CacheConstant {
    private CacheConstant() {}
    /**
     * 构建请求ID缓存Key
     *
     * @param requestId 请求唯一ID，不可为null或空
     * @return 缓存Key字符串
     */
    public static String requestIdKey(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("请求id不能为空");
        }
        return "REQUEST::" + requestId;
    }

    /**
     * 构建火车余票缓存Key
     *
     * @param trainNum      火车车次，不可为null或空
     * @param date         日期，格式 yyyy-MM-dd，不可为null或空
     * @param seatType 座位类型
     * @return 缓存Key字符串
     */
    public static String trainTicketRemainingKey(String trainNum, String date,int seatType) {
        Objects.requireNonNull(trainNum, "trainId must not be null");
        Objects.requireNonNull(date, "date must not be null");
//        Objects.requireNonNull(fromStation, "fromStation must not be null");
//        Objects.requireNonNull(toStation, "toStation must not be null");

        if (trainNum.isEmpty() || date.isEmpty() ) {
            throw new IllegalArgumentException("Cache key parameters must not be empty");
        }


        return String.format(TRAIN_TICKET_REMAINING_KEY_TEMPLATE, trainNum, date, seatType);
    }
    /**
     * 构建火车余票详情缓存Key
     *
     * @param trainNum      火车车次，不可为null或空
     * @param date         日期，格式 yyyy-MM-dd，不可为null或空
     * @param fromStation  起始站（相邻区间的起点），不可为null或空
     * @param toStation    终点站（相邻区间的终点），不可为null或空
     * @param  carriageNumber 车厢号
     * @return 缓存Key字符串
     */
    public static String trainTicketDetailKey(String trainNum, String date, String fromStation, String toStation,String carriageNumber) {
        Objects.requireNonNull(trainNum, "trainId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(fromStation, "fromStation must not be null");
        Objects.requireNonNull(toStation, "toStation must not be null");

        if (trainNum.isEmpty() || date.isEmpty() || fromStation.isEmpty() || toStation.isEmpty()) {
            throw new IllegalArgumentException("Cache key parameters must not be empty");
        }


        return String.format(TRAIN_TICKET_DETAIL_KEY_TEMPLATE, trainNum, date, fromStation, toStation,carriageNumber);
    }
    /**
     * 构建火车路线缓存Key
     *
     * @param startRegion      起始城市
     * @param endRegion        目标城市
     * @return 缓存Key字符串
     */
    public static String trainRouteKey(String startRegion,String endRegion){
        return String.format(TRAIN_ROUTE_KEY_TEMPLATE,startRegion,endRegion);
    }
    /**
     * 构建火车座位类型缓存Key
     *
     * @param trainNumber      车次
     * @return 缓存Key字符串
     */
    public static String trainSeatType(String trainNumber){
        return String.format(TRAIN_SEAT_TYPE,trainNumber);
    }
    /**
     * 构建火车站台顺序型缓存Key
     *
     * @param trainNumber      车次
     * @return 缓存Key字符串
     */
    public static String trainStation(String trainNumber){
        return String.format(TRAIN_STATION_KEY_TEMPLATE,trainNumber);
    }
    /**
     * 通用缓存Key构建方法（谨慎使用，无参数校验）
     *
     * @param pattern 格式模板，如 "TICKET::REMAINING::%s::%s::%s-%s"
     * @param params  格式化参数
     * @return 格式化后的Key
     * @throws IllegalArgumentException 如果 pattern 为 null
     */
    public static String buildKey(String pattern, String... params) {
        if (pattern == null) {
            throw new IllegalArgumentException("pattern must not be null");
        }
        return String.format(pattern, (Object[]) params);
    }

    /* ==================== 常量模板（可选保留，供文档或反射使用）==================== */

    // 保留原始常量，便于查看或文档生成（但不推荐直接用于 format）
    public static final String REQUEST_ID_KEY_TEMPLATE = "REQUEST::%s";
    public static final String TRAIN_TICKET_REMAINING_KEY_TEMPLATE = "TICKET::REMAINING::%s::%s::%d";
    public static final String TRAIN_TICKET_DETAIL_KEY_TEMPLATE  = "TICKET::REMAINING::%s::%s::%s-%s::%s";
    public static final String TRAIN_ROUTE_KEY_TEMPLATE="TRAIN::ROUTE::%s::%s";
    public static final String TRAIN_SEAT_TYPE="TRAIN::SEAT_TYPE::%s";
    public static final String TRAIN_STATION_KEY_TEMPLATE="TRAIN::STATION::%s";
}