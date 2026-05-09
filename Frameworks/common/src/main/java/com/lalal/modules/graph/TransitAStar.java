package com.lalal.modules.graph;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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

    /**
     * 最大搜索节点数（防止死循环）
     */
    private static final int MAX_EXPLORED = 100_000;

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
    public AStarResult aStar(String startStation,
                             LocalDateTime departTime,
                             String endStation,
                             Set<String> penalizedEdges,
                             double weightTime,
                             double weightCost,
                             double weightTransfer,
                             double weightPenalize) {
        // 1. 初始化：找到出发站最近的出发节点
        String startKey = findNearestDeparture(startStation, departTime);
        if (startKey == null) {
            // 精确匹配失败，尝试模糊匹配（如 "成都" 匹配 "成都东"）
            startKey = findNearestDepartureFuzzy(startStation, departTime);
            if (startKey == null) return null;
        }

        // 使用节点实际时间而非参数时间，避免时间约束判断错误
        LocalDateTime actualStartTime = graph.getNode(startKey).getTime();
        log.debug("[A*] 起始节点: key={}, actualTime={}, 终点站={}", startKey, actualStartTime, endStation);

        gScore.put(startKey, 0.0);
        double hStart = estimateHeuristic(startStation, endStation);
        fScore.put(startKey, hStart);

        // 优先队列：按 f(n) 排序
        PriorityQueue<AStarState> open = new PriorityQueue<>(
                Comparator.comparingDouble(AStarState::getF)
        );
        open.offer(new AStarState(startKey, 0.0, hStart, 0, actualStartTime));

        // 2. 主循环
        int explored = 0;
        while (!open.isEmpty()) {
            explored++;
            lastExploredCount = explored;
            if (explored > MAX_EXPLORED) {
                log.debug("[A*] 超过最大搜索节点数 {}, 终止", MAX_EXPLORED);
                break;
            }

            AStarState current = open.poll();
            String currentKey = current.nodeKey;

            // 已访问跳过
            if (closed.contains(currentKey)) continue;
            closed.add(currentKey);

            StationTimeNode currentNode = graph.getNode(currentKey);

            // 到达目的地（精确匹配 或 模糊匹配：终点站名包含/被包含于当前站名）
            if (currentNode.getStation().equals(endStation)
                    || currentNode.getStation().contains(endStation)
                    || endStation.contains(currentNode.getStation())) {
                String prevKey=currentKey;
                while(prevKey!=null){
                    penalizedEdges.add(prevKey);
                    prevKey=cameFrom.getOrDefault(prevKey,null);
                }
                return buildResult(startKey, currentKey, current.g, current.totalTransfers);
            }

            // 遍历出边
            for (TransitEdge edge : graph.getEdges(currentKey)) {
                String neighborKey = edge.getToKey();

                if (closed.contains(neighborKey)) continue;

                // 时间约束检查：跳过已开走的列车（出发时间 < 当前时间）
                if (edge.isTrainEdge()) {
                    TrainEdge trainEdge = (TrainEdge) edge;
                    if (trainEdge.getDepartureTime().isBefore(current.time)) continue;
                }

                // g(n) = 实际时间 + 票价折算时间
                double timeCost = edge.getDurationMinutes()*weightTime;
                double costTime = edge.getCost() * weightCost;
                int transferCost = edge.isWaitEdge() ? 1 : 0;
                double penalizedCost= (penalizedEdges.contains(neighborKey)?1:0)*weightPenalize;

                double tentativeG = current.g + timeCost + costTime + transferCost*weightTransfer+penalizedCost;

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
                            : edge.getToKey() != null && graph.hasNode(edge.getToKey())
                                ? graph.getNode(edge.getToKey()).getTime()
                                : current.time.plusMinutes(edge.getDurationMinutes());

                    open.offer(new AStarState(neighborKey, tentativeG, f,
                            current.totalTransfers + transferCost, neighborTime));
                }
            }
        }

        return null; // 无解
    }

    /**
     * 获取最后搜索探索的节点数（调试用）
     */
    public int getLastExploredCount() {
        return lastExploredCount;
    }

    private int lastExploredCount = 0;

    /**
     * A* 批量搜索：找到多条候选路径 惩罚法
     *
     * @param startStation  出发站
     * @param departTime    出发时间
     * @param endStation    目的站
     * @param maxResults    最大结果数
     * @return 路径结果列表
     */
    public List<AStarResult> aStarMulti(String startStation, LocalDateTime departTime,
                                       String endStation, int maxResults) {
        Set<String> penalizedEdges=new HashSet<>();
       List<AStarResult> results=new ArrayList<>();
       for(int i=0;i<maxResults;i++){
           // 每轮搜索重置状态
           gScore.clear();
           fScore.clear();
           cameFrom.clear();
           closed.clear();

           AStarResult result=aStar(
                   startStation,
                   departTime,
                   endStation,
                   penalizedEdges,
                   0.6,
                   0.4,
                   4000,
                   2000
           );
           if (result==null) break;
           results.add(result);
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
     * 模糊匹配出发节点：站名包含关系（如 "成都" 匹配 "成都东"）
     */
    private String findNearestDepartureFuzzy(String station, LocalDateTime time) {
        // 优先匹配图中站名包含查询站的（"成都" ⊂ "成都东"）
        List<StationTimeNode> candidates = graph.getNodes().values().stream()
                .filter(n -> n.isDeparture() && !n.getTime().isBefore(time))
                .filter(n -> n.getStation().contains(station) || station.contains(n.getStation()))
                .sorted(Comparator.comparing(StationTimeNode::getTime))
                .toList();

        if (candidates.isEmpty()) return null;

        // 优先选包含关系更精确的（"成都东".contains("成都") 优于 "成都西".contains("成都")，取最早时间）
        return candidates.get(0).getKey();
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

            TransitEdge edge = findEdge(prevKey, currentKey);
            if (edge != null) {
                edges.add(0, edge);
            }
            currentKey = prevKey;
        }

        StationTimeNode startNode = graph.getNode(startKey);
        StationTimeNode endNode = graph.getNode(endKey);
        long actualMinutes = java.time.Duration.between(startNode.getTime(), endNode.getTime()).toMinutes();
        return AStarResult.builder()
                .endStation(endNode.getStation())
                .endTime(endNode.getTime())
                .totalMinutes(actualMinutes)
                .totalCost(edges.stream().filter(TransitEdge::isTrainEdge).mapToDouble(TransitEdge::getCost).sum())
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
