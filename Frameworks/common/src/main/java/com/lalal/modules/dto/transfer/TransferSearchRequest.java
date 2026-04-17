package com.lalal.modules.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 换乘搜索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 出发站
     */
    private String from;

    /**
     * 目的地
     */
    private String to;

    /**
     * 乘车日期 yyyy-MM-dd
     */
    private String date;

    /**
     * 出发时间（可选，用于过滤早于该时间的车次）
     */
    private String departureTime;

    /**
     * 最大换乘次数（默认3）
     */
    private int maxTransfer = 3;

    /**
     * 最大历时（分钟，默认480=8小时）
     */
    private int maxDuration = 480;

    /**
     * 最小换乘等待时间（分钟，默认30）
     */
    private int minTransferWait = 30;

    /**
     * 最大换乘等待时间（分钟，默认120）
     */
    private int maxTransferWait = 120;

    /**
     * 搜索算法：Dijkstra / AStar
     */
    private String algorithm = "AStar";

    /**
     * 返回方案数量上限（默认10）
     */
    private int limit = 10;
}
