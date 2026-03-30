/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.framework.log.aspectj.GlobalLogAspect;
import com.lalal.modules.dto.response.StationScreenResponseDTO;
import com.lalal.modules.dto.response.StationScreenTrainDTO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.mapper.StationMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.service.StationScreenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 车站大屏服务实现类
 *
 * 提供车站候车大厅大屏展示所需的实时列车信息：
 * - 正晚点状态计算
 * - 检票状态判断
 * - 候车室/站台分配
 * - 剩余时间提示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StationScreenServiceImpl extends ServiceImpl<StationMapper, com.lalal.modules.entity.StationDO> implements StationScreenService {

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 检票开始时间（发车前）
    private static final int CHECK_IN_START_MINUTES_HIGH_SPEED = 15;   // 高铁提前 15 分钟
    private static final int CHECK_IN_START_MINUTES_NORMAL = 20;       // 普速提前 20 分钟

    // 检票停止时间（发车前）
    private static final int CHECK_IN_STOP_MINUTES_BEFORE_DEPARTURE = 3;

    @Override
    public StationScreenResponseDTO getStationScreen(String stationName) {
        LocalDateTime now = LocalDateTime.now();

        // 获取车站信息
        LambdaQueryWrapper<com.lalal.modules.entity.StationDO> stationWrapper = new LambdaQueryWrapper<>();
        stationWrapper.eq(com.lalal.modules.entity.StationDO::getName, stationName);
        com.lalal.modules.entity.StationDO stationDO = this.getOne(stationWrapper);

        if (stationDO == null) {
            log.warn("车站不存在：{}", stationName);
            return null;
        }

        // 查询当日发车的车次
        LocalDate today = LocalDate.from(now.atZone(java.time.ZoneId.systemDefault()).toLocalDate());

        List<TrainStationDO> departures = getDepartureTrainsForToday(stationDO.getId(), today);

        // 转换为车站大屏 DTO
        List<StationScreenTrainDTO> trains = departures.stream()
                .map(departure -> convertToScreenTrain(departure, now))
                .filter(train -> train != null)
                .sorted(Comparator.comparing(StationScreenTrainDTO::getActualDepartureTime))
                .collect(Collectors.toList());

        // 计算统计数据
        int totalTrainsToday = departures.size();
        long onTimeTrains = trains.stream()
                .filter(t -> t.getDelayStatus() == 0 || t.getDelayStatus() == 2)
                .count();
        double onTimeRate = totalTrainsToday > 0 ? (double) onTimeTrains / totalTrainsToday * 100 : 100.0;

        // 公告信息
        List<String> announcements = generateAnnouncements(trains);

        return StationScreenResponseDTO.builder()
                .stationId(stationDO.getId())
                .stationName(stationDO.getName())
                .stationCode(stationDO.getCode())
                .currentTime(now.format(DateTimeFormatter.ofPattern("HH:mm")))
                .currentDate(now.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE")))
                .totalTrainsToday(totalTrainsToday)
                .onTimeRate(Math.round(onTimeRate * 100.0) / 100.0)
                .trains(trains)
                .announcements(announcements)
                .build();
    }

    @Override
    public StationScreenResponseDTO getStationScreenById(Long stationId) {
        // 先查询车站基本信息
        com.lalal.modules.entity.StationDO stationDO = this.getById(stationId);
        if (stationDO == null) {
            log.warn("车站不存在，ID: {}", stationId);
            return null;
        }

        // 复用根据名称查询的逻辑（简化处理）
        return getStationScreen(stationDO.getName());
    }

    /**
     * 获取某车站在某日的发车车次
     */
    private List<TrainStationDO> getDepartureTrainsForToday(Long stationId, LocalDate date) {
        // 查询该车站作为出发站的记录
        LambdaQueryWrapper<TrainStationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainStationDO::getStationId, stationId);
        wrapper.select(TrainStationDO::getId, TrainStationDO::getTrainId, TrainStationDO::getDepartureTime,
                      TrainStationDO::getPlatform, TrainStationDO::getSequence, TrainStationDO::getRunDate);

        List<TrainStationDO> departureTrains = trainStationMapper.selectList(wrapper);

        if (departureTrains.isEmpty()) {
            return departureTrains;
        }

        // 过滤出当天的车次（考虑跨夜的情况）
        List<TrainStationDO> result = new ArrayList<>();
        LocalDateTime todayStart = date.atTime(LocalTime.MIN);
        LocalDateTime todayEnd = date.atTime(LocalTime.MAX).plusDays(1);

        for (TrainStationDO ts : departureTrains) {
            if (ts.getDepartureTime() == null) {
                continue;
            }

            LocalDateTime actualDeparture = todayStart.plusMinutes(ts.getDepartureTime().toMinuteOfDay() / 60);

            // 如果实际发车时间在当天范围内，或者跨夜（凌晨）也在次日范围内
            if ((actualDeparture.isAfter(todayStart) || actualDeparture.equals(todayStart)) &&
                !actualDeparture.isAfter(todayEnd)) {
                result.add(ts);
            }
        }

        // 如果有跨夜车次的情况，也需要考虑
        if (result.isEmpty() && !departureTrains.isEmpty()) {
            // 考虑前一天末班车可能在今日凌晨到达/发车的情况
            result = departureTrains.stream()
                .filter(ts -> ts.getDepartureTime() != null)
                .sorted(Comparator.comparingInt(ts -> ts.getDepartureTime().toMinuteOfDay()))
                .limit(20)
                .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * 将 TrainStationDO 转换为 StationScreenTrainDTO
     */
    private StationScreenTrainDTO convertToScreenTrain(TrainStationDO departure, LocalDateTime serverNow) {
        Long trainId = departure.getTrainId();
        Integer sequence = departure.getSequence();

        // 查询列车基础信息
        TrainDO train = trainMapper.selectById(trainId);
        if (train == null) {
            log.warn("车次信息不存在，trainId: {}", trainId);
            return null;
        }

        // 查询终到站
        String terminalStation = findTerminalStation(trainId, departure.getStationId());

        // 解析发车时间
        LocalTime departureTime = departure.getDepartureTime();
        LocalDateTime actualDepartureTime = calculateActualDepartureTime(serverNow, departureTime, sequence);

        // 正晚点状态计算
        DelayInfo delayInfo = calculateDelayStatus(actualDepartureTime, departureTime, serverNow);

        // 检票状态计算
        CheckInStatus checkInStatus = calculateCheckInStatus(actualDepartureTime, serverNow, train.getTrainType());

        // 候车室和站台分配（模拟数据，后续可从配置表读取）
        String waitingRoom = assignWaitingRoom(sequence);
        String platform = Optional.ofNullable(departure.getPlatform())
                .orElse(assignPlatform(sequence));
        String checkInGate = "A" + (sequence % 10) + "-" + (sequence % 5 + 1);

        // 剩余时间描述
        String remainingTimeDesc = calculateRemainingTimeDesc(delayInfo, checkInStatus, actualDepartureTime, serverNow);

        return StationScreenTrainDTO.builder()
                .trainNumber(train.getTrainNumber())
                .trainType(train.getTrainType())
                .trainTypeName(getTrainTypeName(train.getTrainType()))
                .terminalStation(terminalStation)
                .departureTime(departureTime.format(TIME_FORMATTER))
                .estimatedDepartureTime(delayInfo.getEstimatedDepartureTime())
                .delayStatus(delayInfo.getDelayStatus())
                .delayStatusText(delayInfo.getDelayStatusText())
                .delayMinutes(delayInfo.getDelayMinutes())
                .checkInStatus(checkInStatus.getStatus())
                .checkInStatusText(checkInStatus.getText())
                .waitingRoom(waitingRoom)
                .checkInGate(checkInGate)
                .platform(platform)
                .remainingTimeDesc(remainingTimeDesc)
                .actualDepartureTime(Date.from(actualDepartureTime.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                .build();
    }

    /**
     * 查找终到站
     */
    private String findTerminalStation(Long trainId, Long currentStationId) {
        LambdaQueryWrapper<TrainStationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainStationDO::getTrainId, trainId);
        wrapper.orderByAsc(TrainStationDO::getSequence);

        List<TrainStationDO> stations = trainStationMapper.selectList(wrapper);

        if (stations.isEmpty()) {
            return "未知";
        }

        // 找到当前站点的序号
        for (TrainStationDO station : stations) {
            if (station.getId().equals(currentStationId)) {
                // 取最后一个站点作为终到站
                TrainStationDO last = stations.get(stations.size() - 1);
                return last.getStationName();
            }
        }

        return stations.get(stations.size() - 1).getStationName();
    }

    /**
     * 计算实际发车时间
     */
    private LocalDateTime calculateActualDepartureTime(LocalDateTime serverNow, LocalTime scheduledTime, int sequence) {
        // 获取今天开始的日期
        LocalDate today = serverNow.toLocalDate();

        // 创建计划发车时间
        LocalDateTime plannedTime = today.atTime(scheduledTime);

        // 如果计划发车时间已经过去，则视为明天的列车（跨夜车）
        if (plannedTime.isBefore(serverNow)) {
            plannedTime = plannedTime.plusDays(1);
        }

        return plannedTime;
    }

    /**
     * 计算正晚点状态
     */
    private DelayInfo calculateDelayStatus(LocalDateTime actualTime, LocalTime scheduledTime, LocalDateTime now) {
        Duration diff = Duration.between(actualTime, actualTime); // TODO: 从配置或真实数据获取

        int delayMinutes = 0;
        int status = 0; // 0=正点，1=晚点，2=待定
        String statusText = "正点";
        String estimatedTime = scheduledTime.format(TIME_FORMATTER);

        // 简化逻辑：根据当前时间与发车时间的关系来判断
        Duration fromNow = Duration.between(now, actualTime);
        long minutesFromNow = fromNow.toMinutes();

        if (minutesFromNow <= -CHECK_IN_STOP_MINUTES_BEFORE_DEPARTURE * 60) {
            // 已经发车
            status = 3; // 已开车
            statusText = "已开车";
            delayMinutes = Math.abs((int) minutesFromNow);
        } else if (minutesFromNow < -CHECK_IN_STOP_MINUTES_BEFORE_DEPARTURE) {
            // 即将停止检票，默认按正点处理
            status = 0;
            statusText = "正点";
            delayMinutes = 0;
        } else {
            // 随机生成 10% 的晚点概率用于演示
            if (Math.random() < 0.1) {
                delayMinutes = (int) (Math.random() * 60);
                status = 1;
                statusText = "晚点";
                estimatedTime = actualTime.plusMinutes(delayMinutes).format(TIME_FORMATTER);
            } else {
                status = 0;
                statusText = "正点";
                delayMinutes = 0;
            }
        }

        return new DelayInfo(status, statusText, delayMinutes, estimatedTime);
    }

    /**
     * 计算检票状态
     */
    private CheckInStatus calculateCheckInStatus(LocalDateTime departureTime, LocalDateTime now, Integer trainType) {
        int earlyCheckInMinutes = trainType == 0 ? CHECK_IN_START_MINUTES_HIGH_SPEED : CHECK_IN_START_MINUTES_NORMAL;

        LocalDateTime checkInStart = departureTime.minusMinutes(earlyCheckInMinutes);
        LocalDateTime checkInStop = departureTime.minusMinutes(CHECK_IN_STOP_MINUTES_BEFORE_DEPARTURE);

        int status;
        String text;

        if (now.isBefore(checkInStart)) {
            status = 0;
            text = "未开始检票";
        } else if (now.isBefore(checkInStop)) {
            status = 1;
            text = "正在检票";
        } else if (now.isBefore(departureTime)) {
            status = 2;
            text = "停止检票";
        } else {
            status = 3;
            text = "已开车";
        }

        return new CheckInStatus(status, text);
    }

    /**
     * 分配候车室（简单规则：根据车次序号）
     */
    private String assignWaitingRoom(int sequence) {
        String[] waitingRooms = {"VIP", "1A", "1B", "2A", "2B", "3A", "3B"};
        return waitingRooms[sequence % waitingRooms.length];
    }

    /**
     * 分配站台（简单规则）
     */
    private String assignPlatform(int sequence) {
        if (sequence <= 5) {
            return "1 号站台";
        } else if (sequence <= 10) {
            return "2 号站台";
        } else if (sequence <= 15) {
            return "3 号站台";
        } else {
            return "4 号站台";
        }
    }

    /**
     * 计算剩余时间描述
     */
    private String calculateRemainingTimeDesc(DelayInfo delayInfo, CheckInStatus checkInStatus,
                                              LocalDateTime departureTime, LocalDateTime now) {
        if (checkInStatus.getStatus() == 3) {
            return "已开车";
        }

        if (checkInStatus.getStatus() == 2) {
            return "停止检票";
        }

        long minutesUntilDeparture = Duration.between(now, departureTime).toMinutes();

        if (minutesUntilDeparture <= 0) {
            return "即将发车";
        }

        if (checkInStatus.getStatus() == 1) {
            return "正在检票";
        }

        if (minutesUntilDeparture <= 30) {
            return String.format("约%d分钟后发车", minutesUntilDeparture);
        } else if (minutesUntilDeparture <= 60) {
            return "30 分钟内发车";
        } else if (minutesUntilDeparture <= 120) {
            return "1 小时内发车";
        } else {
            return String.format("%d分钟后发车", minutesUntilDeparture);
        }
    }

    /**
     * 生成公告信息
     */
    private List<String> generateAnnouncements(List<StationScreenTrainDTO> trains) {
        List<String> announcements = new ArrayList<>();

        // 统计晚点车次
        long delayedCount = trains.stream()
                .filter(t -> t.getDelayStatus() == 1)
                .count();

        if (delayedCount > 0) {
            announcements.add(String.format("温馨提示：今日共有%d趟列车晚点，请广大旅客关注车站大屏公告", delayedCount));
        }

        // 添加一条固定公告
        announcements.add("春运期间，请提前到达车站办理乘车手续");

        return announcements;
    }

    /**
     * 获取列车类型名称
     */
    private String getTrainTypeName(Integer trainType) {
        switch (trainType) {
            case 0: return "高速动车";
            case 1: return "动车组";
            case 2: return "普通列车";
            default: return "其他";
        }
    }

    // 内部类定义

    @lombok.AllArgsConstructor
    @lombok.Data
    private static class DelayInfo {
        private final int delayStatus;
        private final String delayStatusText;
        private final int delayMinutes;
        private final String estimatedDepartureTime;
    }

    @lombok.AllArgsConstructor
    @lombok.Data
    private static class CheckInStatus {
        private final int status;
        private final String text;
    }
}
