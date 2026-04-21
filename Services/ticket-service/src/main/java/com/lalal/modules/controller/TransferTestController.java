package com.lalal.modules.controller;

import com.lalal.modules.dto.transfer.TransferRouteResult;
import com.lalal.modules.dto.transfer.TransferSegment;
import com.lalal.modules.graph.*;
import com.lalal.modules.service.TransferSearchService;
import com.lalal.modules.service.impl.LocalGraphBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 换乘搜索测试接口
 * 提供预置数据的测试，不依赖数据库
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TransferTestController {

    private final TransferSearchService transferSearchService;
    private final LocalGraphBuilder localGraphBuilder;

    private static TransitGraph graph =null;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 测试1：使用预置数据测试 Dijkstra 算法
     *
     * 测试场景：
     * 北京 08:00 出发 → 到达广州
     * 方案1：G1(北京→武汉) → 等候2h → G2(武汉→广州) [总计8h, ¥700]
     * 方案2：G3(北京→长沙) → 等候2h → G4(长沙→广州) [总计10h, ¥600]
     */
    @GetMapping("/dijkstra")
    public Map<String, Object> testDijkstra() {
        log.info("测试 Dijkstra 算法");


        // 执行 Dijkstra 搜索
        TransitDijkstra dijkstra = new TransitDijkstra(graph);

        List<TransitDijkstra.PathResult> results = dijkstra.dijkstraToAll(
                "北京",
                LocalDateTime.of(2024, 1, 1, 8, 0),
                10,
                480
        );

        // 转换为响应格式
        List<Map<String, Object>> routeList = new ArrayList<>();
        for (TransitDijkstra.PathResult r : results) {
            if (r.getEndStation().equals("广州")) {
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("endStation", r.getEndStation());
                route.put("totalMinutes", r.getTotalMinutes());
                route.put("totalCost", r.getTotalCost());
                route.put("transferCount", r.getTransferCount());
                route.put("edges", formatEdges(r.getEdges()));
                routeList.add(route);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("algorithm", "Dijkstra");
        response.put("graph", Map.of(
                "nodes", graph.nodeCount(),
                "edges", graph.edgeCount()
        ));
        response.put("routes", routeList);

        return response;
    }

    /**
     * 测试2：使用预置数据测试 A* 算法
     */
    @GetMapping("/astar")
    public Map<String, Object> testAStar() {
        log.info("测试 A* 算法");


        TransitAStar aStar = new TransitAStar(graph);

        List<TransitAStar.AStarResult> results = aStar.aStarMulti(
                "北京",
                LocalDateTime.of(2024, 1, 1, 8, 0),
                "广州",
                10
        );

        // 转换为响应格式
        List<Map<String, Object>> routeList = new ArrayList<>();
        for (TransitAStar.AStarResult r : results) {
            if (r.getEndStation().equals("广州")) {
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("endStation", r.getEndStation());
                route.put("totalMinutes", r.getTotalMinutes());
                route.put("totalCost", r.getTotalCost());
                route.put("transferCount", r.getTransferCount());
                route.put("edges", formatEdges(r.getEdges()));
                routeList.add(route);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("algorithm", "A*");
        response.put("graph", Map.of(
                "nodes", graph.nodeCount(),
                "edges", graph.edgeCount()
        ));
        response.put("routes", routeList);

        return response;
    }

    /**
     * 测试3：对比 Dijkstra 和 A*
     */
    @GetMapping("/compare")
    public Map<String, Object> compareAlgorithms() {
        TransitGraph graph = buildTestGraph();
        LocalDateTime departTime = LocalDateTime.of(2024, 1, 1, 8, 0);

        // Dijkstra
        TransitDijkstra dijkstra = new TransitDijkstra(graph);
        long dijkstraStart = System.nanoTime();
        List<TransitDijkstra.PathResult> dijkstraResults = dijkstra.dijkstraToAll("北京", departTime, 10, 480);
        long dijkstraTime = System.nanoTime() - dijkstraStart;

        // A*
        TransitAStar aStar = new TransitAStar(graph);
        long aStarStart = System.nanoTime();
        List<TransitAStar.AStarResult> aStarResults = aStar.aStarMulti("北京", departTime, "广州", 10);
        long aStarTime = System.nanoTime() - aStarStart;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("graph", Map.of(
                "nodes", graph.nodeCount(),
                "edges", graph.edgeCount()
        ));
        response.put("dijkstra", Map.of(
                "timeMs", dijkstraTime / 1_000_000.0,
                "resultCount", dijkstraResults.size(),
                "fastestRoute", dijkstraResults.stream()
                        .filter(r -> r.getEndStation().equals("广州"))
                        .findFirst()
                        .map(r -> Map.of("minutes", r.getTotalMinutes(), "cost", r.getTotalCost()))
                        .orElse(null)
        ));
        response.put("astar", Map.of(
                "timeMs", aStarTime / 1_000_000.0,
                "resultCount", aStarResults.size(),
                "fastestRoute", aStarResults.stream()
                        .filter(r -> r.getEndStation().equals("广州"))
                        .findFirst()
                        .map(r -> Map.of("minutes", r.getTotalMinutes(), "cost", r.getTotalCost()))
                        .orElse(null)
        ));

        return response;
    }

    /**
     * 测试4：格式化输出换乘方案（模拟真实 API 响应）
     */
    @GetMapping("/formatted")
    public Map<String, Object> testFormattedRoutes() {
        TransitAStar aStar = new TransitAStar(graph);
        List<TransitAStar.AStarResult> results = aStar.aStarMulti(
                "北京",
                LocalDateTime.of(2024, 1, 1, 8, 0),
                "广州",
                5
        );

        // 过滤只保留到达广州的路径
        List<TransferRouteResult> formattedRoutes = results.stream()
                .filter(r -> r.getEndStation().equals("广州"))
                .map(this::formatAsRouteResult)
                .sorted(Comparator.comparingInt(TransferRouteResult::getTotalMinutes))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("algorithm", "A*");
        response.put("from", "北京");
        response.put("to", "广州");
        response.put("date", "2024-01-01");
        response.put("departTime", "08:00");
        response.put("totalResults", formattedRoutes.size());
        response.put("routes", formattedRoutes);

        return response;
    }

    /**
     * 测试5：打印图结构（调试用）
     */
    @GetMapping("/graph")
    public Map<String, Object> printGraph() {
        graph=buildTestGraph();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nodes", graph.nodeCount());
        response.put("edges", graph.edgeCount());
        response.put("stations", graph.getAllStations());

        // 打印邻接表
        Map<String, List<String>> adjacencyList = new LinkedHashMap<>();
        for (String nodeKey : graph.getAllNodeKeys()) {
            List<String> edges = graph.getEdges(nodeKey).stream()
                    .map(TransitEdge::getEdgeKey)
                    .toList();
            adjacencyList.put(nodeKey, edges);
        }
        response.put("adjacencyList", adjacencyList);

        return response;
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试图
     *
     * 拓扑结构：
     * 北京 --G1--> 武汉 --G2--> 广州
     *  \                    /
     *   --G3--> 长沙--------
     *
     * 详细时刻表：
     * G1: 北京 08:00 → 武汉 12:00 (4h, ¥300)
     * G2: 武汉 14:00 → 广州 18:00 (4h, ¥400)
     * G3: 北京 09:00 → 长沙 13:00 (4h, ¥250)
     * G4: 长沙 15:00 → 广州 19:00 (4h, ¥350)
     */
    private TransitGraph buildTestGraph() {
        TransitGraph graph = new TransitGraph();

        graph=localGraphBuilder.buildFullGraph(LocalDate.now());
//        // G1: 北京 → 武汉 (08:00 → 12:00, 4h, ¥300)
//        graph.addTrainEdge("G1", 0, "北京",
//                LocalDateTime.of(2024, 1, 1, 8, 0),
//                "武汉",
//                LocalDateTime.of(2024, 1, 1, 12, 0),
//                Arrays.asList(1, 2, 3),
//                Arrays.asList(
//                        new TrainEdge.SeatPrice(1, new BigDecimal(300)),  // 二等座
//                        new TrainEdge.SeatPrice(2, new BigDecimal(500)),  // 一等座
//                        new TrainEdge.SeatPrice(3, new BigDecimal(900))   // 商务座
//                ),
//                Arrays.asList(
//                        new TrainEdge.SeatRemaining(1, 100),
//                        new TrainEdge.SeatRemaining(2, 50),
//                        new TrainEdge.SeatRemaining(3, 10)
//                ));
//
//        // G2: 武汉 → 广州 (14:00 → 18:00, 4h, ¥400)
//        graph.addTrainEdge("G2", 0, "武汉",
//                LocalDateTime.of(2024, 1, 1, 14, 0),
//                "广州",
//                LocalDateTime.of(2024, 1, 1, 18, 0),
//                Arrays.asList(1, 2, 3),
//                Arrays.asList(
//                        new TrainEdge.SeatPrice(1, new BigDecimal(400)),
//                        new TrainEdge.SeatPrice(2, new BigDecimal(700)),
//                        new TrainEdge.SeatPrice(3, new BigDecimal(1200))
//                ),
//                Arrays.asList(
//                        new TrainEdge.SeatRemaining(1, 80),
//                        new TrainEdge.SeatRemaining(2, 30),
//                        new TrainEdge.SeatRemaining(3, 5)
//                ));
//
//        // G3: 北京 → 长沙 (09:00 → 13:00, 4h, ¥250)
//        graph.addTrainEdge("G3", 0, "北京",
//                LocalDateTime.of(2024, 1, 1, 9, 0),
//                "长沙",
//                LocalDateTime.of(2024, 1, 1, 13, 0),
//                Arrays.asList(1, 2),
//                Arrays.asList(
//                        new TrainEdge.SeatPrice(1, new BigDecimal(250)),
//                        new TrainEdge.SeatPrice(2, new BigDecimal(450))
//                ),
//                Arrays.asList(
//                        new TrainEdge.SeatRemaining(1, 120),
//                        new TrainEdge.SeatRemaining(2, 60)
//                ));
//
//        // G4: 长沙 → 广州 (15:00 → 19:00, 4h, ¥350)
//        graph.addTrainEdge("G4", 0, "长沙",
//                LocalDateTime.of(2024, 1, 1, 15, 0),
//                "广州",
//                LocalDateTime.of(2024, 1, 1, 19, 0),
//                Arrays.asList(1, 2, 3),
//                Arrays.asList(
//                        new TrainEdge.SeatPrice(1, new BigDecimal(350)),
//                        new TrainEdge.SeatPrice(2, new BigDecimal(600)),
//                        new TrainEdge.SeatPrice(3, new BigDecimal(1000))
//                ),
//                Arrays.asList(
//                        new TrainEdge.SeatRemaining(1, 90),
//                        new TrainEdge.SeatRemaining(2, 40),
//                        new TrainEdge.SeatRemaining(3, 8)
//                ));
//
//        // 添加换乘等待边
//        graph.addTransferWaitEdges("北京");
//        graph.addTransferWaitEdges("武汉");
//        graph.addTransferWaitEdges("长沙");
//        graph.addTransferWaitEdges("广州");

        return graph;
    }

    /**
     * 格式化边列表
     */
    private List<Map<String, Object>> formatEdges(List<TransitEdge> edges) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TransitEdge edge : edges) {
            Map<String, Object> edgeMap = new LinkedHashMap<>();
            edgeMap.put("from", StationTimeNode.parseStation(edge.getFromKey()));
            edgeMap.put("to", StationTimeNode.parseStation(edge.getToKey()));
            edgeMap.put("type", edge.isTrainEdge() ? "TRAIN" : "WAIT");
            edgeMap.put("durationMinutes", edge.getDurationMinutes());
            edgeMap.put("cost", edge.getCost());

            if (edge.isTrainEdge()) {
                TrainEdge trainEdge = (TrainEdge) edge;
                edgeMap.put("trainNumber", trainEdge.getTrainNumber());
                edgeMap.put("departureTime", trainEdge.getDepartureTime().format(TIME_FMT));
                edgeMap.put("arrivalTime", trainEdge.getArrivalTime().format(TIME_FMT));
            } else {
                edgeMap.put("description", "等候换乘");
            }

            result.add(edgeMap);
        }
        return result;
    }

    /**
     * 格式化为 TransferRouteResult
     */
    private TransferRouteResult formatAsRouteResult(TransitAStar.AStarResult r) {
        List<TransferSegment> segments = new ArrayList<>();
        String currentTrain = null;
        TransferSegment currentSegment = null;

        for (TransitEdge edge : r.getEdges()) {
            if (edge.isWaitEdge()) {
                // 换乘，结束当前段
                if (currentSegment != null) {
                    segments.add(currentSegment);
                }
                currentSegment = null;
                currentTrain = null;
            } else if (edge.isTrainEdge()) {
                TrainEdge trainEdge = (TrainEdge) edge;

                if (!trainEdge.getTrainNumber().equals(currentTrain)) {
                    // 新车次
                    if (currentSegment != null) {
                        segments.add(currentSegment);
                    }
                    currentTrain = trainEdge.getTrainNumber();
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
                } else {
                    // 同车次，合并区间
                    currentSegment.setArrivalStation(trainEdge.getArrivalStation());
                    currentSegment.setArrivalTime(trainEdge.getArrivalTime().format(TIME_FMT));
                    currentSegment.setDurationMinutes(
                            currentSegment.getDurationMinutes() + (int) trainEdge.getDurationMinutes()
                    );
                }
            }
        }

        if (currentSegment != null) {
            segments.add(currentSegment);
        }

        return TransferRouteResult.builder()
                .routeId(UUID.randomUUID().toString().substring(0, 8))
                .fromStation(segments.isEmpty() ? "北京" : segments.get(0).getDepartureStation())
                .toStation("广州")
                .departureTime(segments.isEmpty() ? "08:00" : segments.get(0).getDepartureTime())
                .arrivalTime(segments.isEmpty() ? "--:--" : segments.get(segments.size() - 1).getArrivalTime())
                .totalMinutes((int) r.getTotalMinutes())
                .totalPrice(BigDecimal.valueOf(r.getTotalCost()))
                .transferCount(r.getTransferCount())
                .segments(segments)
                .hasAvailableSeats(true)
                .score(r.getTotalMinutes())
                .build();
    }
}
