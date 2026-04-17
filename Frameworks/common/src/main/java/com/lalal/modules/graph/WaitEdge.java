package com.lalal.modules.graph;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 等待边：表示在某个车站等待换乘
 * 例如：G1列车10:00到达武汉，等待G2列车14:00从武汉出发
 * 等待边: 武汉@10:00-到达 -> 武汉@14:00-出发
 */
public class WaitEdge extends TransitEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 等待所在车站
     */
    private final String station;

    /**
     * 到达节点 key
     */
    private final String arrivalNodeKey;

    /**
     * 出发节点 key
     */
    private final String departureNodeKey;

    public WaitEdge(StationTimeNode arrivalNode,
                   StationTimeNode departureNode,
                   String station) {
        super(arrivalNode.getKey(), departureNode.getKey(),
              (int) java.time.Duration.between(arrivalNode.getTime(), departureNode.getTime()).toMinutes(),
              0, EdgeType.WAIT);
        this.station = station;
        this.arrivalNodeKey = arrivalNode.getKey();
        this.departureNodeKey = departureNode.getKey();
    }

    /**
     * 等待时间（分钟）
     */
    public int getWaitMinutes() {
        return (int) durationMinutes;
    }

    @Override
    public String toString() {
        return String.format("WaitEdge[%s: 等候%d分钟]", station, durationMinutes);
    }
}
