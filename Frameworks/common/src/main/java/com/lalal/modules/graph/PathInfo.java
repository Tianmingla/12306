package com.lalal.modules.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 路径信息（算法内部使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PathInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 从起点到当前节点的总耗时（分钟）
     */
    private long totalMinutes;

    /**
     * 从起点到当前节点的总票价
     */
    private double totalCost;

    /**
     * 路径上的边列表
     */
    private List<TransitEdge> edges;

    /**
     * 经过的车站列表（用于展示）
     */
    private List<String> stations;

    /**
     * 添加一条边
     */
    public void addEdge(TransitEdge edge) {
        if (edges == null) edges = new ArrayList<>();
        edges.add(edge);
        totalMinutes += edge.getDurationMinutes();
        totalCost += edge.getCost();
    }

    /**
     * 添加一条边（带车站名）
     */
    public void addEdge(TransitEdge edge, String station) {
        addEdge(edge);
        if (stations == null) stations = new ArrayList<>();
        stations.add(station);
    }

    /**
     * 获取换乘次数（等待边数量）
     */
    public int getTransferCount() {
        if (edges == null) return 0;
        return (int) edges.stream().filter(TransitEdge::isWaitEdge).count();
    }

    /**
     * 复制路径信息
     */
    public PathInfo copy() {
        return PathInfo.builder()
                .totalMinutes(this.totalMinutes)
                .totalCost(this.totalCost)
                .edges(new ArrayList<>(this.edges != null ? this.edges : List.of()))
                .stations(new ArrayList<>(this.stations != null ? this.stations : List.of()))
                .build();
    }
}
