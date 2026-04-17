package com.lalal.modules.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 交通图的边基类
 * 两种子类型：TrainEdge（乘车边）+ WaitEdge（等待边）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class TransitEdge implements Serializable, Comparable<TransitEdge> {

    private static final long serialVersionUID = 1L;

    /**
     * 起始节点 key
     */
    protected String fromKey;

    /**
     * 终止节点 key
     */
    protected String toKey;

    /**
     * 边的权重1：耗时（分钟）
     */
    protected long durationMinutes;

    /**
     * 边的权重2：票价
     */
    protected double cost;

    /**
     * 边的类型：TRAIN / WAIT
     */
    protected EdgeType edgeType;

    public enum EdgeType {
        TRAIN,   // 乘车边
        WAIT     // 等候边（换乘等待）
    }

    /**
     * 获取边的唯一标识
     */
    public String getEdgeKey() {
        return fromKey + "->" + toKey;
    }

    /**
     * 判断是否是乘车边
     */
    public boolean isTrainEdge() {
        return edgeType == EdgeType.TRAIN;
    }

    /**
     * 判断是否是等待边
     */
    public boolean isWaitEdge() {
        return edgeType == EdgeType.WAIT;
    }

    /**
     * 按耗时排序（升序）
     */
    @Override
    public int compareTo(TransitEdge o) {
        return Long.compare(this.durationMinutes, o.durationMinutes);
    }
}
