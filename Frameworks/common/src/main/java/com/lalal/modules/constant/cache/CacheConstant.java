package com.lalal.modules.constant.cache;

import java.util.Objects;
/**
 * 本项目设计原则：
 * 接口设计原则 用唯一code或其他容易人类识别做接口
 * 缓存设计原则 用id做唯一缓存键
 * */
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
     * @param trainId      火车车次id，不可为null或空
     * @param date         日期，格式 yyyy-MM-dd，不可为null或空
     * @param seatType 座位类型
     * @return 缓存Key字符串
     */
    public static String trainTicketRemainingKey(Long trainId, String date,int seatType) {
        Objects.requireNonNull(trainId, "trainId must not be null");
        Objects.requireNonNull(date, "date must not be null");
//        Objects.requireNonNull(fromStation, "fromStation must not be null");
//        Objects.requireNonNull(toStation, "toStation must not be null");

        if ( date.isEmpty() ) {
            throw new IllegalArgumentException("Cache key parameters must not be empty");
        }


        return String.format(TRAIN_TICKET_REMAINING_KEY_TEMPLATE, trainId, date, seatType);
    }
    /**
     * 构建火车余票详情缓存Key
     *
     * @param trainId      火车id，不可为null或空
     * @param date         日期，格式 yyyy-MM-dd，不可为null或空

     * @param  carriageNumber 车厢号
     * @return 缓存Key字符串
     */
    public static String trainTicketDetailKey(Long trainId, String date,String carriageNumber) {
        return String.format(TRAIN_TICKET_DETAIL_KEY_TEMPLATE, trainId, date, carriageNumber);
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
     * @param trainId      车次id
     * @return 缓存Key字符串
     */
    public static String trainSeatType(Long trainId){
        return String.format(TRAIN_SEAT_TYPE,trainId);
    }
    /**
     * 构建火车站台顺序型缓存Key
     *
     * @param trainId      车次id
     * @return 缓存Key字符串
     */
    public static String trainStation(Long trainId){
        return String.format(TRAIN_STATION_KEY_TEMPLATE,trainId);
    }
    /**
     * 构建火车站台顺序型详情缓存Key
     *
     * @param trainId      车次id
     * @return 缓存Key字符串
     */
    public static String trainStationDetail(Long trainId){
        return String.format(TRAIN_STATION_DETAIL_KEY_TEMPLATE,trainId);
    }
    /**
     * 构建站台详情缓存Key
     *
     * @param stationName     站台名
     * @return 缓存Key字符串
     */
    public static String trainStationDetail(String stationName){
        return String.format(TRAIN_STATION_DETAIL_KEY_TEMPLATE,stationName);
    }
    /**
     * 构建火车车次到详情映射的缓存Key
     *
     * @param trainNum     车次号
     * @return 缓存Key字符串
     */
    public static String trainCodeToDetail(String trainNum){
        return String.format(TRAIN_CODE_TO_DETAIL_TEMPLATE,trainNum);
    }
    /**
     * 构建火车的车厢缓存key
     * @param trainId 火车id
     */
    public static String trainCarriage(Long trainId){
        return String.format(TRAIN_CARRIAGE_KEY_TEMPLATE,trainId);
    }

    /**
     * 构建车厢的座位数key
     */
    public static String trainCarriageCount(Long trainId,String carriage_num){
        return String.format(TRAIN_CARRIAGE_COUNT,trainId,carriage_num);
    }

    /**
     * 构建车厢的类别数key
     * @param trainId
     * @param seatType
     * @return
     */
    public static String trainSeatCountKey(Long trainId, Integer seatType) {
        return String.format(TRAIN_SEAT_COUNT,trainId,seatType);
    }
    /**
     * 构建关于站台的火车站台的详情列表数key
     */
    public static String trainStationDetailList(Long stationId){
        return String.format(TRAIN_STATION_DETAIL,stationId);
    }

    //------------------------用户------------------------------------------------------
    /**
     * 构建用户名缓存
     */
    public static String userDetailByName(String name){
        return String.format("USER::DETAIL::%s",name);
    }
    /**
     * 构建id缓存
     */
    public static String userDetailById(long id){
        return String.format("USER::DETAIL::%x",id);
    }
    /**
     * 构建idCard缓存
     */
    public static String userDetailByIdCard(String idCard){
        return String.format("USER::DETAIL::%s",idCard);
    }
    /**
     * 手机号登录账号缓存
     */
    public static String userDetailByPhone(String phone) {
        return String.format("USER::DETAIL::PHONE::%s", phone);
    }
    /**
     * 短信登录验证码
     */
    public static String smsLoginCodeKey(String phone) {
        return String.format("SMS::LOGIN::CODE::%s", phone);
    }
    /**
     * 短信发送频率限制（同一手机号）
     */
    public static String smsSendRateLimitKey(String phone) {
        return String.format("SMS::LOGIN::RATE::%s", phone);
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
    public static final String TRAIN_TICKET_DETAIL_KEY_TEMPLATE  = "TICKET::DETAIL::%s::%s::%s";
    public static final String TRAIN_ROUTE_KEY_TEMPLATE="TRAIN::ROUTE::%s::%s";
    public static final String TRAIN_SEAT_TYPE="TRAIN::SEAT_TYPE::%s";
    public static final String TRAIN_STATION_KEY_TEMPLATE="TRAIN::STATION::%s";
    public static final String TRAIN_STATION_DETAIL_KEY_TEMPLATE="TRAIN::STATION::DETAIL::%s";
    public static final String TRAIN_CARRIAGE_KEY_TEMPLATE="TRAIN::CARRIAGE::%s";
    public static final String  TRAIN_CODE_TO_DETAIL_TEMPLATE = "TRAIN::CODE::%s";
    public static final String  TRAIN_CARRIAGE_COUNT = "TRAIN::CARRIAGE::%s::%s";
    public static final String  TRAIN_SEAT_COUNT = "TRAIN::CARRIAGE::%s::%s";
    public static final String  TRAIN_STATION_DETAIL = "TRAIN::STATION::%s";

    /* ==================== 票价相关缓存Key ==================== */

    public static final String STATION_DISTANCE_KEY_TEMPLATE = "FARE::DISTANCE::%s::%s::%s";
    public static final String TRAIN_FARE_CONFIG_KEY_TEMPLATE = "FARE::CONFIG::%s";
    public static final String PRE_CALCULATED_FARE_KEY_TEMPLATE = "FARE::PRE::%s::%s::%s::%d";

    /**
     * 构建站间距离缓存Key
     *
     * @param trainId 列车ID
     * @param depStation 出发站名称
     * @param arrStation 到达站名称
     * @return 缓存Key字符串
     */
    public static String stationDistanceKey(Long trainId, String depStation, String arrStation) {
        Objects.requireNonNull(trainId, "trainId must not be null");
        if (depStation == null || depStation.isEmpty() || arrStation == null || arrStation.isEmpty()) {
            throw new IllegalArgumentException("站点名称不能为空");
        }
        return String.format(STATION_DISTANCE_KEY_TEMPLATE, trainId, depStation, arrStation);
    }

    /**
     * 构建列车票价配置缓存Key
     *
     * @param trainId 列车ID
     * @return 缓存Key字符串
     */
    public static String trainFareConfigKey(Long trainId) {
        Objects.requireNonNull(trainId, "trainId must not be null");
        return String.format(TRAIN_FARE_CONFIG_KEY_TEMPLATE, trainId);
    }

    /**
     * 构建预计算票价缓存Key
     *
     * @param trainId 列车ID
     * @param depStation 出发站名称
     * @param arrStation 到达站名称
     * @param seatType 座位类型
     * @return 缓存Key字符串
     */
    public static String preCalculatedFareKey(Long trainId, String depStation, String arrStation, int seatType) {
        Objects.requireNonNull(trainId, "trainId must not be null");
        if (depStation == null || depStation.isEmpty() || arrStation == null || arrStation.isEmpty()) {
            throw new IllegalArgumentException("站点名称不能为空");
        }
        return String.format(PRE_CALCULATED_FARE_KEY_TEMPLATE, trainId, depStation, arrStation, seatType);
    }

}