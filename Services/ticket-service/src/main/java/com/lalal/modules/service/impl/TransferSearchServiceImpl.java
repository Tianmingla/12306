package com.lalal.modules.service.impl;

import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.dto.transfer.TransferRouteResult;
import com.lalal.modules.dto.transfer.TransferSearchRequest;
import com.lalal.modules.dto.transfer.TransferSegment;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.graph.*;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.TicketRemainingService;
import com.lalal.modules.service.TransferSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 换乘搜索服务实现
 *
 * 核心流程：
 * 1. LocalGraphBuilder 构建局部图
 * 2. TransitDijkstra / TransitAStar 搜索路径
 * 3. TicketRemainingService 计算余票
 * 4. FareCalculationService 计算票价
 * 5. 转换为 TransferRouteResult 响应格式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransferSearchServiceImpl implements TransferSearchService {

    private final LocalGraphBuilder localGraphBuilder;
    private final TicketRemainingService ticketRemainingService;
    private final FareCalculationService fareCalculationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 评分权重
    private static final double WEIGHT_TIME = 0.5;
    private static final double WEIGHT_COST = 0.3;
    private static final double WEIGHT_TRANSFER = 0.2;

    @Override
    public List<TransferRouteResult> search(TransferSearchRequest request) {
        String algorithm = request.getAlgorithm();
        if ("Dijkstra".equalsIgnoreCase(algorithm)) {
            return searchByDijkstraImpl(request);
        } else {
            return searchByAStarImpl(request);
        }
    }

    @Override
    public List<TransferRouteResult> search(String from, String to, String date) {
        TransferSearchRequest request = TransferSearchRequest.builder()
                .from(from)
                .to(to)
                .date(date)
                .build();
        return search(request);
    }

    @Override
    public List<TransferRouteResult> searchByDijkstra(String from, String to, String date) {
        TransferSearchRequest request = TransferSearchRequest.builder()
                .from(from)
                .to(to)
                .date(date)
                .algorithm("Dijkstra")
                .build();
        return searchByDijkstraImpl(request);
    }

    @Override
    public List<TransferRouteResult> searchByAStar(String from, String to, String date) {
        TransferSearchRequest request = TransferSearchRequest.builder()
                .from(from)
                .to(to)
                .date(date)
                .algorithm("AStar")
                .build();
        return searchByAStarImpl(request);
    }

    // ==================== 内部实现 ====================

    /**
     * Dijkstra 实现
     */
    private List<TransferRouteResult> searchByDijkstraImpl(TransferSearchRequest request) {
        LocalDate date = LocalDate.parse(request.getDate(), DATE_FMT);
        LocalDateTime departTime = request.getDepartureTime() != null
                ? date.atTime(LocalDateTime.parse(request.getDepartureTime(), TIME_FMT).toLocalTime())
                : date.atStartOfDay();

        // 1. 构建局部图
        TransitGraph graph = localGraphBuilder.buildLocalGraph(
                request.getFrom(), request.getTo(), date,
                request.getMaxTransfer(), request.getMaxDuration()
        );

        if (graph.nodeCount() == 0) {
            log.warn("图为空，无法搜索: from={}, to={}", request.getFrom(), request.getTo());
            return Collections.emptyList();
        }

        // 2. 执行 Dijkstra 搜索
        TransitDijkstra dijkstra = new TransitDijkstra(graph);
        List<TransitDijkstra.PathResult> rawResults = dijkstra.dijkstraToAll(
                request.getFrom(), departTime,
                request.getLimit() * 2, // 多搜索一些，后面过滤
                request.getMaxDuration()
        );

        // 3. 过滤和转换
        return rawResults.stream()
                .filter(r -> !r.getEndStation().equals(request.getFrom())) // 排除起点
                .filter(r -> !r.getEndStation().equals(request.getTo()) // 排除中间站（只保留到达目的地）
                        || true) // 暂时保留所有结果
                .filter(r -> r.getTotalMinutes() <= request.getMaxDuration()) // 过滤超时
                .map(r -> convertToResult(r, request.getTo(), request.getDate()))
                .sorted(Comparator.comparingDouble(TransferRouteResult::getScore))
                .limit(request.getLimit())
                .collect(Collectors.toList());
    }

    /**
     * A* 实现
     */
    private List<TransferRouteResult> searchByAStarImpl(TransferSearchRequest request) {
        LocalDate date = LocalDate.parse(request.getDate(), DATE_FMT);
        LocalDateTime departTime = request.getDepartureTime() != null
                ? date.atTime(LocalDateTime.parse(request.getDepartureTime(), TIME_FMT).toLocalTime())
                : date.atStartOfDay();

        // 1. 构建局部图
        TransitGraph graph = localGraphBuilder.buildLocalGraph(
                request.getFrom(), request.getTo(), date,
                request.getMaxTransfer(), request.getMaxDuration()
        );

        if (graph.nodeCount() == 0) {
            log.warn("图为空，无法搜索: from={}, to={}", request.getFrom(), request.getTo());
            return Collections.emptyList();
        }

        // 2. 执行 A* 搜索
        TransitAStar aStar = new TransitAStar(graph);
        List<TransitAStar.AStarResult> rawResults = aStar.aStarMulti(
                request.getFrom(), departTime, request.getTo(),
                request.getLimit() * 3
        );

        // 3. 过滤和转换
        return rawResults.stream()
                .filter(r -> !r.getEndStation().equals(request.getFrom()))
                .filter(r -> r.getTotalMinutes() <= request.getMaxDuration())
                .filter(r -> r.getTransferCount() <= request.getMaxTransfer())
                .map(r -> convertToResult(r, request.getTo(), request.getDate()))
                .sorted(Comparator.comparingDouble(TransferRouteResult::getScore))
                .limit(request.getLimit())
                .collect(Collectors.toList());
    }

    /**
     * 转换 Dijkstra 结果为 TransferRouteResult
     */
    private TransferRouteResult convertToResult(TransitDijkstra.PathResult raw, String targetStation, String date) {
        List<TransferSegment> segments = buildSegments(raw.getEdges(), date);

        TransferRouteResult result = TransferRouteResult.builder()
                .routeId(UUID.randomUUID().toString().substring(0, 8))
                .totalMinutes((int) raw.getTotalMinutes())
                .totalPrice(BigDecimal.valueOf(raw.getTotalCost()))
                .transferCount(raw.getTransferCount())
                .segments(segments)
                .hasAvailableSeats(checkAvailability(segments))
                .build();

        // 计算评分
        result.setScore(calculateScore(result));

        // 设置时间和站点
        if (!segments.isEmpty()) {
            result.setFromStation(segments.get(0).getDepartureStation());
            result.setToStation(segments.get(segments.size() - 1).getArrivalStation());
            result.setDepartureTime(segments.get(0).getDepartureTime());
            result.setArrivalTime(segments.get(segments.size() - 1).getArrivalTime());
        }

        return result;
    }

    /**
     * 转换 A* 结果为 TransferRouteResult
     */
    private TransferRouteResult convertToResult(TransitAStar.AStarResult raw, String targetStation, String date) {
        List<TransferSegment> segments = buildSegments(raw.getEdges(), date);

        TransferRouteResult result = TransferRouteResult.builder()
                .routeId(UUID.randomUUID().toString().substring(0, 8))
                .totalMinutes((int) raw.getTotalMinutes())
                .totalPrice(BigDecimal.valueOf(raw.getTotalCost()))
                .transferCount(raw.getTransferCount())
                .segments(segments)
                .hasAvailableSeats(checkAvailability(segments))
                .build();

        result.setScore(calculateScore(result));

        if (!segments.isEmpty()) {
            result.setFromStation(segments.get(0).getDepartureStation());
            result.setToStation(segments.get(segments.size() - 1).getArrivalStation());
            result.setDepartureTime(segments.get(0).getDepartureTime());
            result.setArrivalTime(segments.get(segments.size() - 1).getArrivalTime());
        }

        return result;
    }

    /**
     * 从边列表构建路段列表（包含票价和余票）
     */
    private List<TransferSegment> buildSegments(List<TransitEdge> edges, String date) {
        List<TransferSegment> segments = new ArrayList<>();
        TransferSegment currentSegment = null;
        String currentTrainNumber = null;

        for (TransitEdge edge : edges) {
            if (edge.isWaitEdge()) {
                // 等待边表示换乘，结束当前路段
                if (currentSegment != null) {
                    segments.add(currentSegment);
                }
                currentSegment = null;
                currentTrainNumber = null;
                continue;
            }

            if (edge.isTrainEdge()) {
                TrainEdge trainEdge = (TrainEdge) edge;

                if (currentTrainNumber == null || !currentTrainNumber.equals(trainEdge.getTrainNumber())) {
                    // 新车次，开始新路段
                    if (currentSegment != null) {
                        segments.add(currentSegment);
                    }

                    currentSegment = TransferSegment.builder()
                            .trainNumber(trainEdge.getTrainNumber())
                            .trainType(trainEdge.getTrainType())
                            .departureStation(trainEdge.getDepartureStation())
                            .departureTime(trainEdge.getDepartureTime().format(TIME_FMT))
                            .arrivalStation(trainEdge.getArrivalStation())
                            .arrivalTime(trainEdge.getArrivalTime().format(TIME_FMT))
                            .durationMinutes((int) trainEdge.getDurationMinutes())
                            .seatTypes(trainEdge.getSeatTypes())
                            .build();

                    // 计算票价和余票
                    enrichSegmentWithPriceAndRemaining(currentSegment, trainEdge, date);

                    currentTrainNumber = trainEdge.getTrainNumber();
                } else {
                    // 同一车次的不同区间，合并
                    if (currentSegment != null) {
                        currentSegment.setArrivalStation(trainEdge.getArrivalStation());
                        currentSegment.setArrivalTime(trainEdge.getArrivalTime().format(TIME_FMT));
                        currentSegment.setDurationMinutes(
                                currentSegment.getDurationMinutes() + (int) trainEdge.getDurationMinutes()
                        );
                    }
                }
            }
        }

        // 添加最后一个路段
        if (currentSegment != null) {
            segments.add(currentSegment);
        }

        return segments;
    }

    /**
     * 补充路段的票价和余票信息
     */
    private void enrichSegmentWithPriceAndRemaining(TransferSegment segment, TrainEdge trainEdge, String date) {
        // 计算票价
        Map<String, BigDecimal> priceMap = new HashMap<>();
        Map<String, Integer> remainingMap = new HashMap<>();

        for (Integer seatType : trainEdge.getSeatTypes()) {
            // 计算票价
            FareCalculationRequestDTO fareRequest = FareCalculationRequestDTO.builder()
                    .trainNumber(trainEdge.getTrainNumber())
                    .departureStation(segment.getDepartureStation())
                    .arrivalStation(segment.getArrivalStation())
                    .seatType(seatType)
                    .build();

            try {
                FareCalculationResultDTO fareResult = fareCalculationService.calculateFare(fareRequest);
                priceMap.put(getSeatTypeName(seatType), fareResult.getTotalFare());
            } catch (Exception e) {
                log.warn("票价计算失败: train={}, from={}, to={}, seatType={}",
                        trainEdge.getTrainNumber(), segment.getDepartureStation(),
                        segment.getArrivalStation(), seatType);
                priceMap.put(getSeatTypeName(seatType), BigDecimal.ZERO);
            }

            // 计算余票
//            Integer remaining = ticketRemainingService.getRemainingBySegment(
//                    trainEdge,
//                    date,
//                    seatType,
//                    segment.getDepartureStation(),
//                    segment.getArrivalStation()
//            );
//            remainingMap.put(getSeatTypeName(seatType), remaining != null ? remaining : 0);
        }

        segment.setPriceMap(priceMap);
        segment.setRemainingMap(remainingMap);
    }

    /**
     * 获取座位类型名称
     */
    private String getSeatTypeName(Integer seatType) {
        return SeatType.getDescByCode(seatType);
    }

    /**
     * 检查路段是否有余票
     */
    private boolean checkAvailability(List<TransferSegment> segments) {
        // 简化实现：检查是否有座位类型
        return segments.stream()
                .anyMatch(s -> s.getSeatTypes() != null && !s.getSeatTypes().isEmpty());
    }

    /**
     * 计算综合评分（越小越优）
     * 评分 = w1 × 时间 + w2 × 票价 + w3 × 换乘次数
     */
    private double calculateScore(TransferRouteResult result) {
        double timeScore = result.getTotalMinutes() * WEIGHT_TIME;
        double costScore = result.getTotalPrice().doubleValue() * WEIGHT_COST * 0.1; // 票价权重归一化
        double transferScore = result.getTransferCount() * WEIGHT_TRANSFER * 100; // 换乘惩罚

        return timeScore + costScore + transferScore;
    }
}
