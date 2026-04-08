package com.lalal.modules.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.core.seat.SeatLayout;
import com.lalal.modules.dao.TrainDO;
import com.lalal.modules.dao.TrainStationDO;
import com.lalal.modules.dto.SeatReleaseMessage;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 座位释放消息消费者
 * 监听订单取消/退票/超时消息，释放座位并更新余量
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "seat-release-topic",
    tag = "*",
    consumerGroup = "seat-release-consumer"
)
@RocketMQMessageListener(
    topic = "seat-release-topic",
    consumerGroup = "seat-release-consumer",
    selectorExpression = "*"
)
public class SeatReleaseConsumer extends RocketMQBaseConsumer {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> seatReleaseScript;
    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final SafeCacheTemplate safeCacheTemplate;

    @Override
    protected void doProcess(Object msg) {
        SeatReleaseMessage message=(SeatReleaseMessage) msg;
        log.info("收到座位释放消息: orderSn={}, type={}", message.getOrderSn(), message.getReleaseType());

        if (message.getSeats() == null || message.getSeats().isEmpty()) {
            log.warn("座位列表为空，跳过处理: orderSn={}", message.getOrderSn());
            return;
        }

        try {
            // 1. 获取列车信息
            TrainDO train = safeCacheTemplate.safeGet(
                    CacheConstant.trainCodeToDetail(message.getTrainNum()),
                    () -> trainMapper.selectOne(new LambdaQueryWrapper<TrainDO>()
                            .eq(TrainDO::getTrainNumber, message.getTrainNum())),
                    new TypeReference<TrainDO>() {},
                    3,
                    TimeUnit.DAYS
            );
            if (train == null) {
                log.error("列车不存在: trainNum={}", message.getTrainNum());
                return;
            }

            // 2. 获取站点列表计算区间
            List<String> stations = safeCacheTemplate.safeGet(
                    CacheConstant.trainStation(train.getId()),
                    () -> trainStationMapper.selectList(new LambdaQueryWrapper<TrainStationDO>()
                            .select(TrainStationDO::getStationName)
                            .eq(TrainStationDO::getTrainNumber, message.getTrainNum())
                            .orderByAsc(TrainStationDO::getSequence))
                            .stream()
                            .map(TrainStationDO::getStationName)
                            .toList(),
                    new TypeReference<List<String>>() {},
                    3,
                    TimeUnit.DAYS
            );

            int startSeq = -1;
            int endSeq = -1;
            for (int i = 0; i < stations.size(); i++) {
                if (stations.get(i).equals(message.getStartStation())) {
                    startSeq = i;
                }
                if (stations.get(i).equals(message.getEndStation())) {
                    endSeq = i;
                }
            }

            if (startSeq == -1 || endSeq == -1 || startSeq >= endSeq) {
                log.error("站点区间计算失败: startStation={}, endStation={}",
                        message.getStartStation(), message.getEndStation());
                return;
            }

            // 0-based segment indices
            int startSeg = startSeq - 1;
            int endSeg = endSeq - 2;
            int totalSegs = stations.size() - 1;

            // 3. 按车厢分组处理座位
            // 先按车厢和座位类型分组
            message.getSeats().stream()
                    .collect(Collectors.groupingBy(
                            seat -> seat.getCarriageNumber() + ":" + seat.getSeatType()
                    ))
                    .forEach((key, seats) -> {
                        String[] parts = key.split(":");
                        String carriageNumber = parts[0];
                        Integer seatType = Integer.parseInt(parts[1]);

                        String detailKey = CacheConstant.trainTicketDetailKey(
                                train.getId(), message.getDate(), carriageNumber);
                        String remainingKey = CacheConstant.trainTicketRemainingKey(
                                train.getId(), message.getDate(), seatType);

                        // 获取座位索引 (需要从座位号转换)
                        List<Integer> seatIndices = seats.stream()
                                .map(seat -> getSeatIndex(seat.getSeatNumber(),SeatType.findByCode(seatType)))
                                .filter(idx -> idx >= 0)
                                .collect(Collectors.toList());

                        if (seatIndices.isEmpty()) {
                            return;
                        }

                        String seatsStr = seatIndices.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(","));

                        try {
                            String result = redisTemplate.execute(seatReleaseScript,
                                    List.of(detailKey, remainingKey),
                                    String.valueOf(startSeg),
                                    String.valueOf(endSeg),
                                    seatsStr,
                                    String.valueOf(totalSegs)
                            );

                            log.info("释放座位结果: carriage={}, seats={}, released={}",
                                    carriageNumber, seatsStr, result);
                        } catch (Exception e) {
                            log.error("释放座位失败: carriage={}, seats={}",
                                    carriageNumber, seatsStr, e);
                        }
                    });

            log.info("座位释放完成: orderSn={}", message.getOrderSn());

        } catch (Exception e) {
            log.error("处理座位释放消息异常: orderSn={}", message.getOrderSn(), e);
            throw e; // 抛出异常触发 MQ 重试
        }
    }

    /**
     * 将座位号转换为索引
     * 例如: "A1" -> 0, "B1" -> 1, "A2" -> 座位数_per_row + 0
     */
    private int getSeatIndex(String seatNumber, SeatType seatType) {
        if (seatNumber == null || seatNumber.length() < 2) {
            return -1;
        }
        switch (seatType) {
            case HARD_SEAT:
                SeatLayout.BUSINESS_CLASS.getIndex(seatNumber);
                break;

        }
        int layoutIndex;

        switch (seatType) {
            // --- 高铁/动车 ---
            case BUSINESS:
                // 对应 SeatLayout.BUSINESS_CLASS (索引 0)
                layoutIndex = SeatLayout.BUSINESS_CLASS.getIndex(seatNumber);
                break;

            case FIRST_CLASS:
                // 对应 SeatLayout.FIRST_CLASS (索引 1)
                layoutIndex = SeatLayout.FIRST_CLASS.getIndex(seatNumber);
                break;

            case SECOND_CLASS:
                // 对应 SeatLayout.SECOND_CLASS (索引 2)
                layoutIndex = SeatLayout.SECOND_CLASS.getIndex(seatNumber);
                break;

            // --- 普通列车 ---
            case HARD_SEAT:
                // 对应 SeatLayout.PLAIN_LAYOUT (索引 3)
                layoutIndex = SeatLayout.HARD_SEAT.getIndex(seatNumber);
                break;

            case SOFT_SEAT:
                // 对应 SeatLayout.PLAIN_LAYOUT (索引 3)
                layoutIndex = SeatLayout.SOFT_SEAT.getIndex(seatNumber);
                break;

            case HARD_SLEEPER:
                // 对应 SeatLayout.PLAIN_LAYOUT (索引 3)
                layoutIndex = SeatLayout.HARD_SEAT.getIndex(seatNumber);
                break;

            case SOFT_SLEEPER:
                // 对应 SeatLayout.PLAIN_LAYOUT (索引 3)
                layoutIndex = SeatLayout.SOFT_SLEEPER.getIndex(seatNumber);
                break;

            case NO_SEAT:
                // 无座通常不需要布局索引，或者也归类为普通布局
                layoutIndex = -1;
                break;

            default:
                layoutIndex = -1;
                break;
        }
        return layoutIndex;
    }
}
