package com.lalal.modules.graph;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dijkstra 最短路径算法（标准版）
 *
 * 特点：
 * - 无需启发函数，盲目搜索所有方向
 * - 时间复杂度 O(V²) 或 O(E log V)（使用优先队列）
 * - 找到从起点到所有节点的最短路径
 *
 * 适用于：精确计算，对性能要求不高的场景
 */
@Getter
public class TransitDijkstra {

    /**
     * 图引用
     */
    private final TransitGraph graph;

    /**
     * 距离表：nodeKey -> PathInfo
     */
    private final Map<String, PathInfo> dist;

    /**
     * 前驱表：nodeKey -> prevKey（用于路径重建）
     */
    private final Map<String, String> prev;

    /**
     * 已访问节点集合
     */
    private final Set<String> visited;

    /**
     * 优先队列：按 totalMinutes 排序
     */
    private final java.util.PriorityQueue<DijkstraState> pq;

    public TransitDijkstra(TransitGraph graph) {
        this.graph = graph;
        this.dist = new HashMap<>();
        this.prev = new HashMap<>();
        this.visited = new java.util.HashSet<>();
        this.pq = new java.util.PriorityQueue<>(
                java.util.Comparator.comparingLong(DijkstraState::getTotalMinutes)
        );
    }

    /**
     * Dijkstra 搜索：找到从起点到终点的最短路径
     *
     * @param startStation  出发站
     * @param departTime    出发时间
     * @param endStation    目的站
     * @return 路径结果，null 表示无解
     */
    public PathResult dijkstra(String startStation, LocalDateTime departTime, String endStation) {
        // 1. 初始化起点
        String startKey = StationTimeNode.makeKey(startStation, departTime, true);

        // 检查起点是否存在
        if (!graph.hasNode(startKey)) {
            // 尝试找最近的后续出发节点
            startKey = findNearestDeparture(startStation, departTime);
            if (startKey == null) return null;
        }

        dist.put(startKey, PathInfo.builder().totalMinutes(0).totalCost(0).edges(new ArrayList<>()).build());
        pq.offer(new DijkstraState(startKey, 0, departTime));

        // 2. 主循环
        while (!pq.isEmpty()) {
            DijkstraState current = pq.poll();
            String currentKey = current.nodeKey;

            // 已访问跳过
            if (visited.contains(currentKey)) continue;
            visited.add(currentKey);

            StationTimeNode currentNode = graph.getNode(currentKey);

            // 到达目的地，结束搜索
            if (currentNode.getStation().equals(endStation)) {
                return buildResult(startKey, currentKey, current.totalMinutes);
            }

            // 遍历所有出边
            for (TransitEdge edge : graph.getEdges(currentKey)) {
                String neighborKey = edge.getToKey();

                if (visited.contains(neighborKey)) continue;

                // 时间约束：只能乘坐出发时间在当前时间之后的列车
                if (edge.isTrainEdge()) {
                    TrainEdge trainEdge = (TrainEdge) edge;
                    if (trainEdge.getDepartureTime().isBefore(current.time)) {
                        continue; // 已发车，跳过
                    }
                }

                long newMinutes = current.totalMinutes + edge.getDurationMinutes();
                double newCost = current.cost + edge.getCost();

                // 尝试更新距离
                PathInfo existing = dist.get(neighborKey);
                if (existing == null || newMinutes < existing.getTotalMinutes()) {
                    dist.put(neighborKey, PathInfo.builder()
                            .totalMinutes(newMinutes)
                            .totalCost(newCost)
                            .edges(new ArrayList<>(dist.get(currentKey).getEdges()) {{
                                add(edge);
                            }})
                            .build());
                    prev.put(neighborKey, currentKey);

                    // 计算邻居节点的到达时间
                    LocalDateTime neighborTime;
                    if (edge.isTrainEdge()) {
                        neighborTime = ((TrainEdge) edge).getArrivalTime();
                    } else {
                        neighborTime = current.time.plusMinutes(edge.getDurationMinutes());
                    }

                    pq.offer(new DijkstraState(neighborKey, newMinutes, newCost, neighborTime));
                }
            }
        }

        return null; // 无可达路径
    }

    /**
     * Dijkstra 搜索：找到从起点出发的所有可达路径，按时间排序
     *
     * @param startStation  出发站
     * @param departTime     出发时间
     * @param maxResults     最大结果数
     * @param maxMinutes    最大耗时（分钟）
     * @return 路径结果列表
     */
    public List<PathResult> dijkstraToAll(String startStation, LocalDateTime departTime,
                                          int maxResults, int maxMinutes) {
        String startKey = StationTimeNode.makeKey(startStation, departTime, true);

        if (!graph.hasNode(startKey)) {
            startKey = findNearestDeparture(startStation, departTime);
            if (startKey == null) return List.of();
        }

        dist.put(startKey, PathInfo.builder().totalMinutes(0).totalCost(0).edges(new ArrayList<>()).build());
        pq.offer(new DijkstraState(startKey, 0, 0, departTime));

        List<PathResult> results = new ArrayList<>();

        while (!pq.isEmpty() && results.size() < maxResults) {
            DijkstraState current = pq.poll();
            String currentKey = current.nodeKey;

            if (visited.contains(currentKey)) continue;
            visited.add(currentKey);

            // 超过最大耗时，停止扩展
            if (current.totalMinutes > maxMinutes) break;

            StationTimeNode node = graph.getNode(currentKey);
            PathInfo pathInfo = dist.get(currentKey);

            // 添加结果（每个车站节点作为一个路径终点）
            results.add(PathResult.builder()
                    .endStation(node.getStation())
                    .endTime(node.getTime())
                    .totalMinutes(current.totalMinutes)
                    .totalCost(current.cost)
                    .edges(new ArrayList<>(pathInfo.getEdges()))
                    .transferCount(pathInfo.getTransferCount())
                    .build());

            // 遍历出边
            for (TransitEdge edge : graph.getEdges(currentKey)) {
                String neighborKey = edge.getToKey();
                if (visited.contains(neighborKey)) continue;

                if (edge.isTrainEdge()) {
                    TrainEdge trainEdge = (TrainEdge) edge;
                    if (trainEdge.getDepartureTime().isBefore(current.time)) continue;
                }

                long newMinutes = current.totalMinutes + edge.getDurationMinutes();
                double newCost = current.cost + edge.getCost();

                PathInfo existing = dist.get(neighborKey);
                if (existing == null || newMinutes < existing.getTotalMinutes()) {
                    dist.put(neighborKey, PathInfo.builder()
                            .totalMinutes(newMinutes)
                            .totalCost(newCost)
                            .edges(new ArrayList<>(dist.get(currentKey).getEdges()) {{
                                add(edge);
                            }})
                            .build());
                    prev.put(neighborKey, currentKey);

                    LocalDateTime neighborTime = edge.isTrainEdge()
                            ? ((TrainEdge) edge).getArrivalTime()
                            : current.time.plusMinutes(edge.getDurationMinutes());

                    pq.offer(new DijkstraState(neighborKey, newMinutes, newCost, neighborTime));
                }
            }
        }

        return results;
    }

    /**
     * 找到最近的后续出发节点
     */
    private String findNearestDeparture(String station, LocalDateTime time) {
        return graph.getNodes().values().stream()
                .filter(n -> n.getStation().equals(station) && n.isDeparture() && !n.getTime().isBefore(time))
                .min((a, b) -> a.getTime().compareTo(b.getTime()))
                .map(StationTimeNode::getKey)
                .orElse(null);
    }

    /**
     * 重建路径结果
     */
    private PathResult buildResult(String startKey, String endKey, long totalMinutes) {
        List<TransitEdge> edges = new ArrayList<>();
        double cost = 0;
        int transfers = 0;

        String currentKey = endKey;
        while (currentKey != null && !currentKey.equals(startKey)) {
            PathInfo info = dist.get(currentKey);
            if (info != null && info.getEdges() != null && !info.getEdges().isEmpty()) {
                TransitEdge lastEdge = info.getEdges().get(info.getEdges().size() - 1);
                edges.add(0, lastEdge);
                cost = info.getTotalCost();
                if (lastEdge.isWaitEdge()) transfers++;
            }
            currentKey = prev.get(currentKey);
        }

        StationTimeNode endNode = graph.getNode(endKey);
        return PathResult.builder()
                .endStation(endNode.getStation())
                .endTime(endNode.getTime())
                .totalMinutes(totalMinutes)
                .totalCost(cost)
                .edges(edges)
                .transferCount(transfers)
                .build();
    }

    // ============ 内部类 ============

    /**
     * Dijkstra 算法状态（优先队列元素）
     */
    @Getter
    private static class DijkstraState {
        private final String nodeKey;
        private final long totalMinutes;
        private final double cost;
        private final LocalDateTime time;

        DijkstraState(String nodeKey, long totalMinutes, LocalDateTime time) {
            this(nodeKey, totalMinutes, 0, time);
        }

        DijkstraState(String nodeKey, long totalMinutes, double cost, LocalDateTime time) {
            this.nodeKey = nodeKey;
            this.totalMinutes = totalMinutes;
            this.cost = cost;
            this.time = time;
        }
    }

    /**
     * 路径搜索结果
     */
    @Getter
    @lombok.Builder
    public static class PathResult {
        private final String endStation;
        private final LocalDateTime endTime;
        private final long totalMinutes;
        private final double totalCost;
        private final List<TransitEdge> edges;
        private final int transferCount;
    }
}
