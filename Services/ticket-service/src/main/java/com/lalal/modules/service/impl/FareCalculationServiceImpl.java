package com.lalal.modules.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.entity.StationDistanceDO;
import com.lalal.modules.entity.TrainFareConfigDO;
import com.lalal.modules.enumType.fare.*;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.StationDistanceMapper;
import com.lalal.modules.mapper.TrainFareConfigMapper;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.framework.cache.SafeCacheTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 票价计算服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FareCalculationServiceImpl implements FareCalculationService {

    private final StationDistanceMapper stationDistanceMapper;
    private final TrainFareConfigMapper trainFareConfigMapper;
    private final SafeCacheTemplate safeCacheTemplate;

    // ==================== 起码里程常量 ====================
    /**
     * 客票起码里程 20km
     */
    private static final int MIN_SEAT_DISTANCE = 20;

    /**
     * 加快票起码里程 100km
     */
    private static final int MIN_EXPRESS_DISTANCE = 100;

    /**
     * 卧铺票起码里程 400km
     */
    private static final int MIN_SLEEPER_DISTANCE = 400;

    // ==================== 附加费常量 ====================
    private static final BigDecimal TICKET_DEV_FUND_LOW = new BigDecimal("0.5");
    private static final BigDecimal TICKET_DEV_FUND_HIGH = new BigDecimal("1.0");
    private static final BigDecimal WAITING_ROOM_AC_FEE = new BigDecimal("1.0");
    private static final BigDecimal SLEEPER_BOOKING_FEE = new BigDecimal("10.0");

    /**
     * 保险费率 2%
     */
    private static final BigDecimal INSURANCE_RATE = new BigDecimal("0.02");

    /**
     * 春运上浮率 20%
     */
    private static final BigDecimal PEAK_SEASON_SURCHARGE = new BigDecimal("1.2");

    @Override
    public FareCalculationResultDTO calculateFare(FareCalculationRequestDTO request) {
        FareCalculationResultDTO result = new FareCalculationResultDTO();
        result.setTrainId(request.getTrainId());
        result.setDepartureStation(request.getDepartureStation());
        result.setArrivalStation(request.getArrivalStation());
        result.setSeatType(request.getSeatType());
        result.setPassengerId(request.getPassengerId());

        // 1. 获取站间距离
        Integer distance = getDistance(request.getTrainId(), request.getDepartureStation(), request.getArrivalStation());
        if (distance == null || distance <= 0) {
            distance = getDistanceByTrainNumber(request.getTrainNumber(), request.getDepartureStation(), request.getArrivalStation());
        }
        if (distance == null || distance <= 0) {
            log.warn("无法获取站间距离: trainId={}, trainNumber={}, dep={}, arr={}",
                    request.getTrainId(), request.getTrainNumber(), request.getDepartureStation(), request.getArrivalStation());
            // 使用默认距离（后续应该抛出异常或使用估算距离）
            distance = 100;
        }
        result.setDistance(distance);

        // 2. 获取座位类型和旅客类型
        SeatType seatType = SeatType.findByCode(request.getSeatType());
        if (seatType == null) {
            seatType = SeatType.SECOND_CLASS;
        }
        PassengerTypeEnum passengerType = PassengerTypeEnum.fromCode(
                request.getPassengerType() != null ? request.getPassengerType() : 0);

        // 3. 获取列车上浮类型
        SurchargeTypeEnum surchargeType = getSurchargeType(request);

        // 4. 是否春运期间
        boolean isPeakSeason = Boolean.TRUE.equals(request.getIsPeakSeason());

        // 5. 计算各分项票价
        calculateFareItems(result, distance, seatType, surchargeType, passengerType);

        // 6. 计算附加费
        calculateSurcharges(result, distance, seatType, passengerType);

        // 7. 计算总计
        calculateTotals(result, isPeakSeason);

        return result;
    }

    @Override
    public List<FareCalculationResultDTO> batchCalculateFare(List<FareCalculationRequestDTO> requests) {
        List<FareCalculationResultDTO> results = new ArrayList<>();
        for (FareCalculationRequestDTO request : requests) {
            results.add(calculateFare(request));
        }
        return results;
    }

    @Override
    public Integer getDistance(Long trainId, String departureStation, String arrivalStation) {
        if (trainId == null || departureStation == null || arrivalStation == null) {
            return null;
        }

        String cacheKey = CacheConstant.stationDistanceKey(trainId, departureStation, arrivalStation);

        return safeCacheTemplate.safeGet(
                cacheKey,
                () -> {
                    StationDistanceDO distanceDO = stationDistanceMapper.selectByTrainAndStations(
                            trainId, departureStation, arrivalStation);
                    return distanceDO != null ? distanceDO.getDistance() : null;
                },
                new TypeReference<Integer>() {},
                24, TimeUnit.HOURS
        );
    }

    @Override
    public Integer getDistanceByTrainNumber(String trainNumber, String departureStation, String arrivalStation) {
        if (trainNumber == null || departureStation == null || arrivalStation == null) {
            return null;
        }

        StationDistanceDO distanceDO = stationDistanceMapper.selectByTrainNumberAndStations(
                trainNumber, departureStation, arrivalStation);
        return distanceDO != null ? distanceDO.getDistance() : null;
    }

    // ==================== 私有计算方法 ====================

    /**
     * 获取列车上浮类型
     */
    private SurchargeTypeEnum getSurchargeType(FareCalculationRequestDTO request) {
        // 先查询数据库配置
        if (request.getTrainId() != null) {
            TrainFareConfigDO config = trainFareConfigMapper.selectByTrainId(request.getTrainId());
            if (config != null) {
                return SurchargeTypeEnum.fromCode(config.getSurchargeType());
            }
        }
        if (request.getTrainNumber() != null) {
            TrainFareConfigDO config = trainFareConfigMapper.selectByTrainNumber(request.getTrainNumber());
            if (config != null) {
                return SurchargeTypeEnum.fromCode(config.getSurchargeType());
            }
        }

        // 根据车次品牌推断
        return SurchargeTypeEnum.fromTrainBrand(request.getTrainBrand());
    }

    /**
     * 计算各分项票价
     */
    private void calculateFareItems(FareCalculationResultDTO result, int distance,
                                    SeatType seatType, SurchargeTypeEnum surchargeType,
                                    PassengerTypeEnum passengerType) {
        // 计算硬座基础票价（应用递远递减）
        BigDecimal baseSeatFare = calculateBaseSeatFare(distance);
        result.setSeatFare(BigDecimal.ZERO);

        // 获取详细座位类型（卧铺的上/下铺）
        SeatType detailedSeatType = seatType.getDetailedSleeperType();

        // 根据座位类型计算客票票价
        FarePriceRateEnum seatRate = getSeatFareRate(detailedSeatType);
        if (seatRate != null) {
            BigDecimal seatFare = baseSeatFare.multiply(BigDecimal.valueOf(seatRate.getRateMultiplier()));
            // 应用旅客折扣
            seatFare = applyPassengerDiscount(seatFare, passengerType.getSeatDiscount());
            // 应用上浮率并四舍五入
            seatFare = applySurcharge(seatFare, surchargeType);
            result.setSeatFare(seatFare);
        }

        // 计算保险费（按硬座基本票价2%计算，以角为单位进整）
        BigDecimal insuranceFare = baseSeatFare.multiply(INSURANCE_RATE);
        insuranceFare = roundToJiao(insuranceFare);
        result.setInsuranceFare(insuranceFare);

        // 计算加快票（普快/快速）
        if (isExpressTrain(detailedSeatType)) {
            int expressDistance = Math.max(distance, MIN_EXPRESS_DISTANCE);
            BigDecimal expressBaseFare = calculateBaseSeatFare(expressDistance);
            FarePriceRateEnum expressRate = isFastExpress(detailedSeatType)
                    ? FarePriceRateEnum.FAST_EXPRESS : FarePriceRateEnum.EXPRESS;
            BigDecimal expressFare = expressBaseFare.multiply(BigDecimal.valueOf(expressRate.getRateMultiplier()));
            // 应用旅客折扣
            expressFare = applyPassengerDiscount(expressFare, passengerType.getExpressDiscount());
            // 应用上浮率并四舍五入
            expressFare = applySurcharge(expressFare, surchargeType);
            result.setExpressFare(expressFare);
        }

        // 计算卧铺票
        if (seatType.isSleeper()) {
            int sleeperDistance = Math.max(distance, MIN_SLEEPER_DISTANCE);
            BigDecimal sleeperBaseFare = calculateBaseSeatFare(sleeperDistance);
            FarePriceRateEnum sleeperRate = getSleeperFareRate(detailedSeatType);
            BigDecimal sleeperFare = sleeperBaseFare.multiply(BigDecimal.valueOf(sleeperRate.getRateMultiplier()));
            // 应用旅客折扣
            sleeperFare = applyPassengerDiscount(sleeperFare, passengerType.getSleeperDiscount());
            // 应用上浮率并四舍五入
            sleeperFare = applySurcharge(sleeperFare, surchargeType);
            result.setSleeperFare(sleeperFare);
        }

        // 计算空调票
        if (hasAirConditioning(seatType, surchargeType)) {
            int acDistance = Math.max(distance, MIN_SEAT_DISTANCE);
            BigDecimal acBaseFare = calculateBaseSeatFare(acDistance);
            BigDecimal acFare = acBaseFare.multiply(
                    BigDecimal.valueOf(FarePriceRateEnum.AIR_CONDITIONING.getRateMultiplier()));
            // 应用旅客折扣
            acFare = applyPassengerDiscount(acFare, passengerType.getAcDiscount());
            // 应用上浮率并四舍五入
            acFare = applySurcharge(acFare, surchargeType);
            result.setAcFare(acFare);
        }
    }

    /**
     * 计算硬座基础票价（应用递远递减）
     */
    private BigDecimal calculateBaseSeatFare(int distance) {
        // 处理起码里程
        int effectiveDistance = Math.max(distance, MIN_SEAT_DISTANCE);

        // 应用递远递减计算基础票价
        return DecreasingRateEnum.calculateBaseFare(effectiveDistance);
    }

    /**
     * 计算附加费
     */
    private void calculateSurcharges(FareCalculationResultDTO result, int distance,
                                     SeatType seatType, PassengerTypeEnum passengerType) {
        // 计算旅客票价（用于判断客票发展金）
        BigDecimal passengerFare = result.getSeatFare();
        if (result.getExpressFare() != null) {
            passengerFare = passengerFare.add(result.getExpressFare());
        }
        if (result.getSleeperFare() != null) {
            passengerFare = passengerFare.add(result.getSleeperFare());
        }
        if (result.getAcFare() != null) {
            passengerFare = passengerFare.add(result.getAcFare());
        }
        passengerFare = passengerFare.add(result.getInsuranceFare());

        // 客票发展金：票价≤5元收0.5元，>5元收1元
        BigDecimal ticketDevFund = passengerFare.compareTo(new BigDecimal("5")) <= 0
                ? TICKET_DEV_FUND_LOW : TICKET_DEV_FUND_HIGH;
        result.setTicketDevFund(ticketDevFund);

        // 候车室空调费：硬席旅客乘车>200km收1元
        if (seatType.isHardSeat() && distance > 200) {
            result.setWaitingRoomAcFee(WAITING_ROOM_AC_FEE);
        }

        // 卧铺订票费：购买卧铺票收10元
        if (seatType.isSleeper()) {
            result.setSleeperBookingFee(SLEEPER_BOOKING_FEE);
        }
    }

    /**
     * 计算总计
     */
    private void calculateTotals(FareCalculationResultDTO result, boolean isPeakSeason) {
        // 基本票价 = 客票 + 附加票（加快+卧铺+空调）
        BigDecimal baseFare = result.getSeatFare();
        if (result.getExpressFare() != null) {
            baseFare = baseFare.add(result.getExpressFare());
        }
        if (result.getSleeperFare() != null) {
            baseFare = baseFare.add(result.getSleeperFare());
        }
        if (result.getAcFare() != null) {
            baseFare = baseFare.add(result.getAcFare());
        }
        result.setBaseFare(baseFare);

        // 旅客票价 = 基本票价 + 保险费
        BigDecimal passengerFare = baseFare.add(result.getInsuranceFare());
        result.setPassengerFare(passengerFare);

        // 联合票价 = 旅客票价 + 附加费
        BigDecimal totalFare = passengerFare;
        if (result.getTicketDevFund() != null) {
            totalFare = totalFare.add(result.getTicketDevFund());
        }
        if (result.getWaitingRoomAcFee() != null) {
            totalFare = totalFare.add(result.getWaitingRoomAcFee());
        }
        if (result.getSleeperBookingFee() != null) {
            totalFare = totalFare.add(result.getSleeperBookingFee());
        }

        // 春运期间上浮20%
        if (isPeakSeason) {
            totalFare = totalFare.multiply(PEAK_SEASON_SURCHARGE)
                    .setScale(0, RoundingMode.HALF_UP);
        }

        result.setTotalFare(totalFare);
    }

    // ==================== 辅助方法 ====================

    /**
     * 应用上浮率并四舍五入到元
     */
    private BigDecimal applySurcharge(BigDecimal fare, SurchargeTypeEnum surchargeType) {
        return fare.multiply(BigDecimal.valueOf(surchargeType.getMultiplier()))
                .setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 应用旅客折扣
     */
    private BigDecimal applyPassengerDiscount(BigDecimal fare, double discount) {
        return fare.multiply(BigDecimal.valueOf(discount));
    }

    /**
     * 以角为单位进整
     */
    private BigDecimal roundToJiao(BigDecimal value) {
        // 乘以10，向上取整，再除以10
        return value.multiply(new BigDecimal("10"))
                .setScale(0, RoundingMode.UP)
                .divide(new BigDecimal("10"), 1, RoundingMode.UP);
    }

    /**
     * 根据座位类型获取票价率
     */
    private FarePriceRateEnum getSeatFareRate(SeatType seatType) {
        switch (seatType) {
            case HARD_SEAT:
                return FarePriceRateEnum.HARD_SEAT;
            case SOFT_SEAT:
                return FarePriceRateEnum.SOFT_SEAT;
            case SECOND_CLASS:
                // 二等座按硬座基础计算（高铁有单独定价）
                return FarePriceRateEnum.HARD_SEAT;
            case FIRST_CLASS:
                // 一等座按硬座1.6倍计算（高铁定价）
                return FarePriceRateEnum.HARD_SEAT; // 后续会单独处理
            case BUSINESS:
                // 商务座按硬座3倍计算（高铁定价）
                return FarePriceRateEnum.HARD_SEAT; // 后续会单独处理
            case HARD_SLEEPER_UPPER:
                return FarePriceRateEnum.HARD_SLEEPER_UPPER;
            case HARD_SLEEPER_MIDDLE:
                return FarePriceRateEnum.HARD_SLEEPER_MIDDLE;
            case HARD_SLEEPER_LOWER:
            case HARD_SLEEPER:
                return FarePriceRateEnum.HARD_SLEEPER_LOWER;
            case SOFT_SLEEPER_UPPER:
                return FarePriceRateEnum.SOFT_SLEEPER_UPPER;
            case SOFT_SLEEPER_LOWER:
            case SOFT_SLEEPER:
                return FarePriceRateEnum.SOFT_SLEEPER_LOWER;
            case DELUXE_SLEEPER_UPPER:
                return FarePriceRateEnum.DELUXE_SLEEPER_UPPER;
            case DELUXE_SLEEPER_LOWER:
                return FarePriceRateEnum.DELUXE_SLEEPER_LOWER;
            case NO_SEAT:
                // 无座按硬座计算
                return FarePriceRateEnum.HARD_SEAT;
            default:
                return FarePriceRateEnum.HARD_SEAT;
        }
    }

    /**
     * 获取卧铺票价率
     */
    private FarePriceRateEnum getSleeperFareRate(SeatType seatType) {
        switch (seatType) {
            case HARD_SLEEPER_UPPER:
                return FarePriceRateEnum.HARD_SLEEPER_UPPER;
            case HARD_SLEEPER_MIDDLE:
                return FarePriceRateEnum.HARD_SLEEPER_MIDDLE;
            case HARD_SLEEPER_LOWER:
            case HARD_SLEEPER:
                return FarePriceRateEnum.HARD_SLEEPER_LOWER;
            case SOFT_SLEEPER_UPPER:
                return FarePriceRateEnum.SOFT_SLEEPER_UPPER;
            case SOFT_SLEEPER_LOWER:
            case SOFT_SLEEPER:
                return FarePriceRateEnum.SOFT_SLEEPER_LOWER;
            case DELUXE_SLEEPER_UPPER:
                return FarePriceRateEnum.DELUXE_SLEEPER_UPPER;
            case DELUXE_SLEEPER_LOWER:
                return FarePriceRateEnum.DELUXE_SLEEPER_LOWER;
            default:
                return FarePriceRateEnum.HARD_SLEEPER_LOWER;
        }
    }

    /**
     * 判断是否为快速列车
     */
    private boolean isFastExpress(SeatType seatType) {
        // K字头列车为快速，T字头为特快，Z字头为直达
        // 这里简化处理，根据座位类型判断
        return seatType == SeatType.FIRST_CLASS || seatType == SeatType.BUSINESS;
    }

    /**
     * 判断是否为快车（需要加快票）
     */
    private boolean isExpressTrain(SeatType seatType) {
        // 所有列车都需要加快票（普快或快速）
        // 高铁动车按快速计算
        return true;
    }

    /**
     * 判断是否有空调
     */
    private boolean hasAirConditioning(SeatType seatType, SurchargeTypeEnum surchargeType) {
        // 新型空调车有空调
        if (surchargeType != SurchargeTypeEnum.NORMAL) {
            return true;
        }
        // 高铁动车商务座、一等座、二等座有空调
        // 软卧、软座有空调
        return seatType.hasAirConditioning();
    }
}
