package com.lalal.modules.service;

import com.lalal.modules.dto.transfer.TransferSearchRequest;
import com.lalal.modules.dto.transfer.TransferRouteResult;

import java.util.List;

/**
 * 换乘搜索服务接口
 */
public interface TransferSearchService {

    /**
     * 搜索换乘路线
     *
     * @param request 搜索请求
     * @return 换乘方案列表（按综合评分排序）
     */
    List<TransferRouteResult> search(TransferSearchRequest request);

    /**
     * 搜索换乘路线（使用默认参数）
     *
     * @param from 出发站
     * @param to   目的站
     * @param date 乘车日期
     * @return 换乘方案列表
     */
    List<TransferRouteResult> search(String from, String to, String date);

    /**
     * 使用 Dijkstra 算法搜索
     *
     * @param from 出发站
     * @param to   目的站
     * @param date 乘车日期
     * @return 换乘方案列表
     */
    List<TransferRouteResult> searchByDijkstra(String from, String to, String date);

    /**
     * 使用 A* 算法搜索
     *
     * @param from 出发站
     * @param to   目的站
     * @param date 乘车日期
     * @return 换乘方案列表
     */
    List<TransferRouteResult> searchByAStar(String from, String to, String date);
}
