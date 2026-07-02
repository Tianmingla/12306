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
import com.lalal.modules.template.CompositeKey3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
     * 使用惩罚法 + 多出发时间策略搜索多条候选路径
     */
    private List<TransferRouteResult> searchByAStarImpl(TransferSearchRequest request) {
        LocalDate date = LocalDate.parse(request.getDate(), DATE_FMT);
        LocalDateTime departTime = request.getDepartureTime() != null
                ? date.atTime(LocalDateTime.parse(request.getDepartureTime(), TIME_FMT).toLocalTime())
                : date.atTime(LocalTime.MIN);

        // 1. 构建局部图
        TransitGraph graph = localGraphBuilder.buildLocalGraph(
                request.getFrom(), request.getTo(), date,
                request.getMaxTransfer(), request.getMaxDuration()
        );

        if (graph.nodeCount() == 0) {
            log.warn("图为空，无法搜索: from={}, to={}", request.getFrom(), request.getTo());
            return Collections.emptyList();
        }

        // 2. 执行 A* 搜索（惩罚法 + 多出发时间）
        Set<String> seenRoutes = new HashSet<>();
        List<TransitAStar.AStarResult> rawResults = new ArrayList<>();

        log.info("[A*] 开始搜索: from={}, to={}, nodes={}, edges={}",
                request.getFrom(), request.getTo(), graph.nodeCount(), graph.edgeCount());

        // 2.1 惩罚法：从同一出发时间搜索多条路径
        TransitAStar aStar = new TransitAStar(graph,request.getMaxTransfer(), request.getMaxDuration(),request.getMinTransferWait(),request.getMaxTransferWait());
        loadHeuristicCache(aStar, graph, request.getTo());

        List<TransitAStar.AStarResult> penaltyResults = aStar.aStarMulti(
                request.getFrom(), departTime, request.getTo(),
                request.getLimit()
        );
        for (TransitAStar.AStarResult r : penaltyResults) {
            String routeKey = buildRouteKey(r);
            if (seenRoutes.add(routeKey)) {
                rawResults.add(r);
            }
        }

        // 2.2 多出发时间策略：每隔2小时搜索一次，覆盖不同时段
        int[] hourOffsets = {2, 4, 6, 8, 10};
        for (int offset : hourOffsets) {
            LocalDateTime altDepartTime = departTime.plusHours(offset);
            if (altDepartTime.toLocalDate().isAfter(date)) break;

            // 每个出发时间创建新的 A* 实例，避免状态污染
            TransitAStar altAStar = new TransitAStar(graph,request.getMaxTransfer(), request.getMaxDuration(),request.getMinTransferWait(),request.getMaxTransferWait());
            loadHeuristicCache(altAStar, graph, request.getTo());

            List<TransitAStar.AStarResult> altResults = altAStar.aStarMulti(
                    request.getFrom(), altDepartTime, request.getTo(),
                    Math.max(2, request.getLimit() / 2)
            );
            for (TransitAStar.AStarResult r : altResults) {
                String routeKey = buildRouteKey(r);
                if (seenRoutes.add(routeKey)) {
                    rawResults.add(r);
                }
            }
        }

        if (rawResults.isEmpty()) {
            log.info("[A*] 未找到任何路径: from={}, to={}, explored={}", request.getFrom(), request.getTo(), aStar.getLastExploredCount());
            return Collections.emptyList();
        }

        log.info("[A*] 找到 {} 条候选路径: from={}, to={}", rawResults.size(), request.getFrom(), request.getTo());

        // 3. 过滤和转换
        String fromStation = request.getFrom();
        String toStation = request.getTo();

        // 调试：检查每条路径被哪个 filter 过滤
//        List<TransferRouteResult> converted = rawResults.stream()
//                .peek(r -> {
//                    boolean endMatch = r.getEndStation().equals(fromStation)
//                            || r.getEndStation().contains(fromStation)
//                            || fromStation.contains(r.getEndStation());
//                    boolean durationOk = r.getTotalMinutes() <= request.getMaxDuration();
//                    boolean transferOk = r.getTransferCount() <= request.getMaxTransfer();
//                    if (endMatch || !durationOk || !transferOk) {
//                        log.info("[A*] 过滤掉路径: endStation={}, totalMinutes={}, transfers={}, endMatch={}, durationOk={}, transferOk={}",
//                                r.getEndStation(), r.getTotalMinutes(), r.getTransferCount(), endMatch, durationOk, transferOk);
//                    }
//                })
//                .filter(r -> !r.getEndStation().equals(fromStation) && !r.getEndStation().contains(fromStation) && !fromStation.contains(r.getEndStation()))
//                .filter(r -> r.getTotalMinutes() <= request.getMaxDuration())
//                .filter(r -> r.getTransferCount() <= request.getMaxTransfer())
//                .map(r -> convertToResult(r, request.getTo(), request.getDate()))
//                .sorted(Comparator.comparingDouble(TransferRouteResult::getScore))
//                .limit(request.getLimit())
//                .collect(Collectors.toList());
        List<TransferRouteResult> converted = new ArrayList<>();
        for(TransitAStar.AStarResult result : rawResults){
            TransferRouteResult routeResult=new TransferRouteResult();
            routeResult.setSegments(new ArrayList<>());

            //每个线路的结果统计
            int totalMinutes=0;
            boolean hasAvailableSeats=true;
            BigDecimal totalPrice=new BigDecimal(0);

            //每段行程的结果统计
            Boolean flag=true;
            TransferSegment segment=new TransferSegment();
            for(int i=0;i<result.getEdges().size()+1;i++){
                if(i==result.getEdges().size() || result.getEdges().get(i).getEdgeType()== TransitEdge.EdgeType.TRANSFER_WAIT){
                    if(flag){ //处理第一站就等待的情况
                        continue;
                    }
                    routeResult.getSegments().add(segment);
                    totalMinutes+= (int) (segment.getDurationMinutes());
                    //价格最低
                    BigDecimal price=new BigDecimal(Integer.MAX_VALUE);
                    for(Map.Entry<String,BigDecimal> entry:segment.getPriceMap().entrySet()){
                        price=price.min(entry.getValue());
                    }
                    totalPrice=totalPrice.add(price);
                    if(i==result.getEdges().size()) break;
                }

                TransitEdge edge=result.getEdges().get(i);
                switch (edge.getEdgeType()){
                    case WAIT:
                        WaitEdge waitEdge=(WaitEdge) edge;
                        segment.setDurationMinutes((int) (segment.getDurationMinutes()+waitEdge.getDurationMinutes()));
                        break;
                    case TRAIN:
                        TrainEdge trainEdge = (TrainEdge) edge;
                        if(flag) {
                            segment.setDepartureStation(trainEdge.getDepartureStation());
                            segment.setDepartureTime(TIME_FMT.format(trainEdge.getDepartureTime()));
                            segment.setArrivalStation(trainEdge.getArrivalStation());
                            segment.setArrivalTime(TIME_FMT.format(trainEdge.getArrivalTime()));

                            segment.setTrainNumber(trainEdge.getTrainNumber());
                            segment.setTrainType(trainEdge.getTrainType());

                            segment.setSeatTypes(trainEdge.getSeatTypes());

                            Map<String,BigDecimal> priceMap=new HashMap<>();
                            for(TrainEdge.SeatPrice pair:trainEdge.getSeatPrices()){
                                String seatTypeStr=SeatType.getDescByCode(pair.seatType());
                                priceMap.put(seatTypeStr,pair.price());
                            }
                            segment.setPriceMap(priceMap);

                            //TODO 余票没有填在边里面
                            if(trainEdge.getSeatRemainings()!=null) {
                                Map<String, Integer> remainingMap = new HashMap<>();
                                for (TrainEdge.SeatRemaining pair : trainEdge.getSeatRemainings()) {
                                    String seatTypeStr = SeatType.getDescByCode(pair.seatType());
                                    remainingMap.put(seatTypeStr, pair.remaining());
                                    if (pair.remaining() == 0) hasAvailableSeats = false;
                                }
                                segment.setRemainingMap(remainingMap);
                            }

                            segment.setDurationMinutes((int) trainEdge.getDurationMinutes());
                            flag=false;
                        }else{
                            segment.setArrivalStation(trainEdge.getArrivalStation());
                            segment.setArrivalTime(TIME_FMT.format(trainEdge.getArrivalTime()));

                            segment.setDurationMinutes((int) (segment.getDurationMinutes()+trainEdge.getDurationMinutes()));

                            Map<String,BigDecimal> priceMap=segment.getPriceMap();
                            for(TrainEdge.SeatPrice pair:trainEdge.getSeatPrices()){
                                String seatTypeStr=SeatType.getDescByCode(pair.seatType());
                                priceMap.compute(seatTypeStr, (key, oldValue) -> (oldValue == null) ? new BigDecimal(0) : oldValue.add(pair.price()));
                            }

                            //TODO 余票没有填在边里面
                            if(trainEdge.getSeatRemainings()!=null) {
                                Map<String, Integer> remainingMap = segment.getRemainingMap();
                                for (TrainEdge.SeatRemaining pair : trainEdge.getSeatRemainings()) {
                                    String seatTypeStr = SeatType.getDescByCode(pair.seatType());
                                    remainingMap.compute(seatTypeStr, (key, oldValue) -> (oldValue == null) ? 0 : Math.min(oldValue, pair.remaining()));
                                    if (pair.remaining() == 0) hasAvailableSeats = false;
                                }
                            }
                        }
                        break;
                    case TRANSFER_WAIT:
                        totalMinutes+= (int) (edge.getDurationMinutes());
                        flag=true;
                        segment=new TransferSegment();
                        break;
                }
            }
            routeResult.setTransferCount(routeResult.getSegments().size());
            routeResult.setFromStation(routeResult.getSegments().get(0).getDepartureStation());
            routeResult.setDepartureTime(routeResult.getSegments().get(0).getDepartureTime());
            routeResult.setToStation(routeResult.getSegments().get(routeResult.getSegments().size()-1).getArrivalStation());
            routeResult.setArrivalTime(routeResult.getSegments().get(routeResult.getSegments().size()-1).getArrivalTime());
            routeResult.setTotalMinutes(totalMinutes);
            routeResult.setHasAvailableSeats(hasAvailableSeats);
            routeResult.setTotalPrice(totalPrice);
            converted.add(routeResult);
        }
        log.info("[A*] 过滤后剩余 {} 条路径", converted.size());
        return converted;
    }

    /**
     * 构建路径唯一标识（用于去重）
     * 由各段车次号序列组成
     */
    private String buildRouteKey(TransitAStar.AStarResult result) {
        return result.getEdges().stream()
                .filter(TransitEdge::isTrainEdge)
                .map(e -> ((TrainEdge) e).getTrainNumber())
                .collect(Collectors.joining("->"));
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

    /**
     * 预加载 A* 启发函数的距离数据
     * 从图中提取所有区段距离，Dijkstra 反向计算各站到终点的最短铁路距离
     */
    private void loadHeuristicCache(TransitAStar aStar, TransitGraph graph, String endStation) {
        List<String> stations = graph.getAllStations();
        if (stations.size() < 2) return;

        // 1. 从图中所有 TrainEdge 提取区段距离，构建简化距离图（对相同站对取最短距离）
        Map<String, Map<String, Integer>> distGraph = new HashMap<>();
        for (TransitEdge edge : graph.getAllEdges()) {
            if (!edge.isTrainEdge()) continue;
            TrainEdge te = (TrainEdge) edge;
            if (te.getDistance() == null) continue;
            distGraph.computeIfAbsent(te.getDepartureStation(), k -> new HashMap<>())
                    .merge(te.getArrivalStation(), te.getDistance(), Math::min);
        }

        // 2. Dijkstra 反向计算各站到终点的最短铁路距离
        Map<String, Double> heuristicData = dijkstraReverse(distGraph, endStation);

        if (!heuristicData.isEmpty()) {
            aStar.setHeuristicCache(heuristicData);
            log.info("[A*] 启发函数距离数据已加载: {} 条, 终点站={}", heuristicData.size(), endStation);
        }
    }

    /**
     * 反向 Dijkstra：从终点站出发，计算各站到终点的最短铁路距离
     * 用于 A* 启发函数 h(n) = distance(n, target) / AVG_SPEED
     */
    private Map<String, Double> dijkstraReverse(Map<String, Map<String, Integer>> distGraph, String target) {
        // 构建反向图：arriveStation -> departStation
        Map<String, Map<String, Integer>> reverseGraph = new HashMap<>();
        for (var entry : distGraph.entrySet()) {
            for (var inner : entry.getValue().entrySet()) {
                reverseGraph.computeIfAbsent(inner.getKey(), k -> new HashMap<>())
                        .merge(entry.getKey(), inner.getValue(), Math::min);
            }
        }

        Map<String, Integer> dist = new HashMap<>();
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));
        dist.put(target, 0);
        pq.offer(Map.entry(target, 0));

        while (!pq.isEmpty()) {
            var curr = pq.poll();
            String u = curr.getKey();
            int d = curr.getValue();
            if (d > dist.getOrDefault(u, Integer.MAX_VALUE)) continue;

            for (var neighbor : reverseGraph.getOrDefault(u, Map.of()).entrySet()) {
                String v = neighbor.getKey();
                int newDist = d + neighbor.getValue();
                if (newDist < dist.getOrDefault(v, Integer.MAX_VALUE)) {
                    dist.put(v, newDist);
                    pq.offer(Map.entry(v, newDist));
                }
            }
        }

        // 转换为 heuristicData：key="站A_终点站" value=距离(km)
        Map<String, Double> result = new HashMap<>();
        for (var entry : dist.entrySet()) {
            if (!entry.getKey().equals(target)) {
                result.put(entry.getKey() + "_" + target, entry.getValue().doubleValue());
            }
        }
        return result;
    }
}
