package com.lalal.modules.graph;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 交通换乘图（时间展开图）
 *
 * 节点：StationTimeNode = (站点名, 时间戳, 是否出发)
 * 边：
 *   - TrainEdge：乘车边（某车次从A站到B站）
 *   - WaitEdge：等待边（同站等待换乘）
 *
 * 构建流程：
 * 1. 添加列车运行数据，构建所有节点的乘车边
 * 2. 添加等待边（同站到达→出发）
 * 3. 搜索最短路径
 */
@Getter
public class TransitGraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点集合：nodeKey -> StationTimeNode
     */
    private final Map<String, StationTimeNode> nodes = new HashMap<>();

    /**
     * 边集合：fromKey -> List<TransitEdge>
     */
    private final Map<String, List<TransitEdge>> adjacency = new HashMap<>();

    /**
     * 节点数量
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * 边数量
     */
    public int edgeCount() {
        return adjacency.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 添加节点
     */
    public void addNode(StationTimeNode node) {
        nodes.putIfAbsent(node.getKey(), node);
    }

    /**
     * 获取节点
     */
    public StationTimeNode getNode(String nodeKey) {
        return nodes.get(nodeKey);
    }

    /**
     * 检查节点是否存在
     */
    public boolean hasNode(String nodeKey) {
        return nodes.containsKey(nodeKey);
    }

    /**
     * 添加边
     */
    public void addEdge(TransitEdge edge) {
        adjacency.computeIfAbsent(edge.getFromKey(), k -> new ArrayList<>()).add(edge);
    }

    /**
     * 获取某节点的所有出边
     */
    public List<TransitEdge> getEdges(String nodeKey) {
        return adjacency.getOrDefault(nodeKey, List.of());
    }

    /**
     * 获取某节点的所有出边（过滤类型）
     */
    public List<TransitEdge> getEdges(String nodeKey, TransitEdge.EdgeType edgeType) {
        return adjacency.getOrDefault(nodeKey, List.of()).stream()
                .filter(e -> e.getEdgeType() == edgeType)
                .collect(Collectors.toList());
    }

    /**
     * 添加一条乘车边（自动创建节点）
     */
    public void addTrainEdge(String trainNumber,
                             int trainType,
                             String departureStation,
                             LocalDateTime departureTime,
                             String arrivalStation,
                             LocalDateTime arrivalTime,
                             List<Integer> seatTypes,
                             List<TrainEdge.SeatPrice> seatPrices,
                             List<TrainEdge.SeatRemaining> seatRemainings) {

        // 创建出发节点和到达节点
        StationTimeNode departNode = new StationTimeNode(departureStation, departureTime, true);
        StationTimeNode arriveNode = new StationTimeNode(arrivalStation, arrivalTime, false);

        addNode(departNode);
        addNode(arriveNode);

        // 创建乘车边
        TrainEdge trainEdge = new TrainEdge(
                departNode, arriveNode,
                trainNumber, trainType,
                departureStation, arrivalStation,
                departureTime, arrivalTime,
                seatTypes, seatPrices, seatRemainings
        );
        addEdge(trainEdge);

        // 创建等待边（同站：到达 → 下一班出发）
        // 注意：这个方法会在后续添加同站等待边时调用
    }

    /**
     * 添加等待边（换乘等待）
     */
    public void addWaitEdge(StationTimeNode arrivalNode, StationTimeNode departureNode) {
        if (arrivalNode == null || departureNode == null) return;
        if (!arrivalNode.getStation().equals(departureNode.getStation())) return;
        if (arrivalNode.isDeparture() || !departureNode.isDeparture()) return;

        WaitEdge waitEdge = new WaitEdge(arrivalNode, departureNode, arrivalNode.getStation());
        addEdge(waitEdge);
    }

    /**
     * 批量添加同一车站的等待边
     * 遍历该车站的所有"到达"节点和"出发"节点，配对生成等待边
     */
    public void addTransferWaitEdges(String station) {
        // 找出该车站的所有到达节点和出发节点
        List<StationTimeNode> arrivals = nodes.values().stream()
                .filter(n -> n.getStation().equals(station) && !n.isDeparture())
                .sorted((a, b) -> a.getTime().compareTo(b.getTime()))
                .collect(Collectors.toList());

        List<StationTimeNode> departures = nodes.values().stream()
                .filter(n -> n.getStation().equals(station) && n.isDeparture())
                .sorted((a, b) -> a.getTime().compareTo(b.getTime()))
                .collect(Collectors.toList());

        // 为每个到达节点，连接后续的出发节点（允许等待换乘）
        for (StationTimeNode arrival : arrivals) {
            for (StationTimeNode departure : departures) {
                if (departure.getTime().isAfter(arrival.getTime())) {
                    addWaitEdge(arrival, departure);
                }
            }
        }
    }

    /**
     * 获取所有车站列表
     */
    public List<String> getAllStations() {
        return nodes.values().stream()
                .map(StationTimeNode::getStation)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 获取所有节点 key 列表
     */
    public List<String> getAllNodeKeys() {
        return new ArrayList<>(nodes.keySet());
    }

    /**
     * 打印图结构（调试用）
     */
    public void printGraph() {
        System.out.println("=== TransitGraph ===");
        System.out.printf("Nodes: %d, Edges: %d%n", nodeCount(), edgeCount());
        System.out.println("Stations: " + getAllStations());

        System.out.println("\n--- Adjacency List ---");
        for (Map.Entry<String, List<TransitEdge>> entry : adjacency.entrySet()) {
            System.out.printf("%s -> %s%n", entry.getKey(),
                    entry.getValue().stream().map(TransitEdge::getEdgeKey).collect(Collectors.joining(", ")));
        }
    }
}
