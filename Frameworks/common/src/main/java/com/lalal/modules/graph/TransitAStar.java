package com.lalal.modules.graph;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/**
 * A* 最短路径算法（启发式搜索版）
 *
 * 特点：
 * - f(n) = g(n) + h(n)
 *   g(n): 从起点到 n 的实际成本
 *   h(n): 从 n 到终点的预估成本（启发函数）
 * - 利用启发函数引导搜索方向，大幅减少搜索节点
 * - 若 h(n) ≤ 真实最小值，算法必找到最优解
 *
 * 适用于：已知目的地，快速找到最优路径（铁路换乘场景）
 */
@Getter
public class TransitAStar {

    /**
     * 图引用
     */
    private final TransitGraph graph;

    /**
     * g(n)：实际成本表
     */
    private final Map<String, Double> gScore;

    /**
     * f(n)：评估成本表
     */
    private final Map<String, Double> fScore;

    /**
     * 前驱表
     */
    private final Map<String, String> cameFrom;

    /**
     * 已关闭节点
     */
    private final Set<String> closed;

    /**
     * 启发函数：station -> 到各终点的预估时间（分钟）
     * Key: "stationA_stationB" -> 直线距离 / 平均速度
     */
    private final Map<String, Double> heuristicCache;

    /**
     * 平均高铁速度（km/h）
     */
    private static final double AVG_SPEED_KMH = 200.0;

    public TransitAStar(TransitGraph graph) {
        this.graph = graph;
        this.gScore = new HashMap<>();
        this.fScore = new HashMap<>();
        this.cameFrom = new HashMap<>();
        this.closed = new HashSet<>();
        this.heuristicCache = new HashMap<>();
    }

    /**
     * A* 搜索：找到从起点到终点的最短路径
     *
     * @param startStation  出发站
     * @param departTime    出发时间
     * @param endStation    目的站
     * @param weightTime    时间权重（默认0.5）
     * @param weightCost    票价权重（默认0.3）
     * @param weightTransfer 换乘权重（默认0.2）
     * @return 路径结果，null 表示无解
     */
    public AStarResult aStar(String startStation, LocalDateTime departTime,
                             String endStation,
                             double weightTime, double weightCost, double weightTransfer) {
        // 1. 初始化
        String startKey = StationTimeNode.makeKey(startStation, departTime, true);

        if (!graph.hasNode(startKey)) {
            startKey = findNearestDeparture(startStation, departTime);
            if (startKey == null) return null;
        }

        gScore.put(startKey, 0.0);
        double hStart = estimateHeuristic(startStation, endStation);
        fScore.put(startKey, hStart);

        // 优先队列：按 f(n) 排序
        PriorityQueue<AStarState> open = new PriorityQueue<>(
                Comparator.comparingDouble(AStarState::getF)
        );
        open.offer(new AStarState(startKey, 0.0, hStart, 0, departTime));

        // 2. 主循环
        while (!open.isEmpty()) {
            AStarState current = open.poll();
            String currentKey = current.nodeKey;

            // 已访问跳过
            if (closed.contains(currentKey)) continue;
            closed.add(currentKey);

            StationTimeNode currentNode = graph.getNode(currentKey);

            // 到达目的地
            if (currentNode.getStation().equals(endStation)) {
                return buildResult(startKey, currentKey, current.g, current.totalTransfers);
            }

            // 遍历出边
            for (TransitEdge edge : graph.getEdges(currentKey)) {
                String neighborKey = edge.getToKey();

                if (closed.contains(neighborKey)) continue;

                // 时间约束检查
                if (edge.isTrainEdge()) {
                    TrainEdge trainEdge = (TrainEdge) edge;
                    if (trainEdge.getDepartureTime().isBefore(current.time)) continue;
                }

                // g(n) = 实际时间 + 票价折算时间
                double timeCost = edge.getDurationMinutes();
                double costTime = edge.getCost() * 0.1; // 票价每100元=10分钟代价
                int transferCost = edge.isWaitEdge() ? 1 : 0;

                double tentativeG = current.g + timeCost + costTime + transferCost * 50;

                Double existingG = gScore.get(neighborKey);
                if (existingG == null || tentativeG < existingG) {
                    cameFrom.put(neighborKey, currentKey);
                    gScore.put(neighborKey, tentativeG);

                    StationTimeNode neighborNode = graph.getNode(neighborKey);
                    double h = estimateHeuristic(neighborNode.getStation(), endStation);

                    // f(n) = g(n) + h(n)
                    double f = tentativeG + h;
                    fScore.put(neighborKey, f);

                    LocalDateTime neighborTime = edge.isTrainEdge()
                            ? ((TrainEdge) edge).getArrivalTime()
                            : current.time.plusMinutes(edge.getDurationMinutes());

                    open.offer(new AStarState(neighborKey, tentativeG, f,
                            current.totalTransfers + transferCost, neighborTime));
                }
            }
        }

        return null; // 无解
    }

    /**
     * A* 批量搜索：找到多条候选路径
     *
     * @param startStation  出发站
     * @param departTime    出发时间
     * @param endStation    目的站
     * @param maxResults    最大结果数
     * @return 路径结果列表
     */
    public List<AStarResult> aStarMulti(String startStation, LocalDateTime departTime,
                                       String endStation, int maxResults) {
        String startKey = StationTimeNode.makeKey(startStation, departTime, true);

        if (!graph.hasNode(startKey)) {
            startKey = findNearestDeparture(startStation, departTime);
            if (startKey == null) return List.of();
        }

        gScore.put(startKey, 0.0);
        fScore.put(startKey, estimateHeuristic(startStation, endStation));

        PriorityQueue<AStarState> open = new PriorityQueue<>(
                Comparator.comparingDouble(AStarState::getF)
        );
        open.offer(new AStarState(startKey, 0.0, estimateHeuristic(startStation, endStation),
                0, departTime));

        List<AStarResult> results = new ArrayList<>();
        Set<String> addedResults = new HashSet<>();

        while (!open.isEmpty() && results.size() < maxResults) {
            AStarState current = open.poll();
            String currentKey = current.nodeKey;

            if (closed.contains(currentKey)) continue;
            closed.add(currentKey);

            StationTimeNode currentNode = graph.getNode(currentKey);

            // 每到达一个新车站，记录一条路径
            String stationKey = currentNode.getStation();
            if (!addedResults.contains(stationKey)) {
                addedResults.add(stationKey);
                results.add(buildResult(startKey, currentKey, current.g, current.totalTransfers));
            }

            // 遍历出边
            for (TransitEdge edge : graph.getEdges(currentKey)) {
                String neighborKey = edge.getToKey();

                if (closed.contains(neighborKey)) continue;

                if (edge.isTrainEdge()) {
                    TrainEdge trainEdge = (TrainEdge) edge;
                    if (trainEdge.getDepartureTime().isBefore(current.time)) continue;
                }

                double tentativeG = current.g + edge.getDurationMinutes() + edge.getCost() * 0.1
                        + (edge.isWaitEdge() ? 50 : 0);

                if (gScore.getOrDefault(neighborKey, Double.MAX_VALUE) > tentativeG) {
                    cameFrom.put(neighborKey, currentKey);
                    gScore.put(neighborKey, tentativeG);

                    StationTimeNode neighborNode = graph.getNode(neighborKey);
                    double f = tentativeG + estimateHeuristic(neighborNode.getStation(), endStation);
                    fScore.put(neighborKey, f);

                    LocalDateTime neighborTime = edge.isTrainEdge()
                            ? ((TrainEdge) edge).getArrivalTime()
                            : current.time.plusMinutes(edge.getDurationMinutes());

                    open.offer(new AStarState(neighborKey, tentativeG, f,
                            current.totalTransfers + (edge.isWaitEdge() ? 1 : 0), neighborTime));
                }
            }
        }

        // 按总耗时排序
        results.sort(Comparator.comparingDouble(AStarResult::getTotalMinutes));
        return results;
    }

    /**
     * 启发函数：预估从 stationA 到 stationB 的时间（分钟）
     *
     * 使用直线距离 / 平均速度估算
     * 实际应用中应预加载 stationDistanceMap
     */
    private double estimateHeuristic(String stationA, String stationB) {
        if (stationA.equals(stationB)) return 0;

        // 尝试从缓存获取
        String key = stationA + "_" + stationB;
        String keyReverse = stationB + "_" + stationA;

        if (heuristicCache.containsKey(key)) {
            return heuristicCache.get(key);
        }
        if (heuristicCache.containsKey(keyReverse)) {
            return heuristicCache.get(keyReverse);
        }

        // 估算：假设两站间平均距离 300km，平均速度 200km/h
        // 即 1.5 小时 = 90 分钟
        double estimatedMinutes = 90.0;
        heuristicCache.put(key, estimatedMinutes);

        return estimatedMinutes;
    }

    /**
     * 设置启发函数参数（预加载站点距离数据）
     */
    public void setHeuristicCache(Map<String, Double> distanceMap) {
        // distanceMap: "北京_武汉" -> 距离(km)
        distanceMap.forEach((key, distance) -> {
            // 将距离转换为时间：distance / 200 * 60 分钟
            double minutes = distance / AVG_SPEED_KMH * 60;
            heuristicCache.put(key, minutes);
            // 双向存储
            String reverseKey = key.substring(key.indexOf('_') + 1) + "_" + key.substring(0, key.indexOf('_'));
            heuristicCache.put(reverseKey, minutes);
        });
    }

    /**
     * 找到最近的后续出发节点
     */
    private String findNearestDeparture(String station, LocalDateTime time) {
        return graph.getNodes().values().stream()
                .filter(n -> n.getStation().equals(station) && n.isDeparture() && !n.getTime().isBefore(time))
                .min(Comparator.comparing(StationTimeNode::getTime))
                .map(StationTimeNode::getKey)
                .orElse(null);
    }

    /**
     * 重建结果
     */
    private AStarResult buildResult(String startKey, String endKey, double totalG, int transfers) {
        List<TransitEdge> edges = new ArrayList<>();
        String currentKey = endKey;

        while (currentKey != null && !currentKey.equals(startKey)) {
            String prevKey = cameFrom.get(currentKey);
            if (prevKey == null) break;

            PathInfo info = gScore.containsKey(currentKey) ? null : null;
            // 从 gScore 反推边（简化版）
            TransitEdge edge = findEdge(prevKey, currentKey);
            if (edge != null) {
                edges.add(0, edge);
            }
            currentKey = prevKey;
        }

        StationTimeNode endNode = graph.getNode(endKey);
        return AStarResult.builder()
                .endStation(endNode.getStation())
                .endTime(endNode.getTime())
                .totalMinutes((long) totalG)
                .totalCost(edges.stream().mapToDouble(TransitEdge::getCost).sum())
                .edges(edges)
                .transferCount(transfers)
                .build();
    }

    /**
     * 查找边（简化实现）
     */
    private TransitEdge findEdge(String fromKey, String toKey) {
        List<TransitEdge> edges = graph.getEdges(fromKey);
        for (TransitEdge edge : edges) {
            if (edge.getToKey().equals(toKey)) {
                return edge;
            }
        }
        return null;
    }

    // ============ 内部类 ============

    /**
     * A* 算法状态
     */
    @Getter
    private static class AStarState {
        private final String nodeKey;
        private final double g;          // 实际成本
        private final double f;          // 评估成本
        private final int totalTransfers;
        private final LocalDateTime time;

        AStarState(String nodeKey, double g, double f, int totalTransfers, LocalDateTime time) {
            this.nodeKey = nodeKey;
            this.g = g;
            this.f = f;
            this.totalTransfers = totalTransfers;
            this.time = time;
        }
    }

    /**
     * A* 搜索结果
     */
    @Getter
    @lombok.Builder
    public static class AStarResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String endStation;
        private final LocalDateTime endTime;
        private final long totalMinutes;
        private final double totalCost;
        private final List<TransitEdge> edges;
        private final int transferCount;

        /**
         * 综合评分
         */
        public double getScore(double weightTime, double weightCost, double weightTransfer) {
            return weightTime * totalMinutes + weightCost * totalCost * 0.1
                    + weightTransfer * transferCount * 50;
        }
    }
}
