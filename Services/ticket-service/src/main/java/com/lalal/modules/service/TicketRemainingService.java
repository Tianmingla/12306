package com.lalal.modules.service;

import com.lalal.modules.dto.TicketRemainingRequestDTO;
import com.lalal.modules.dto.TicketRemainingResultDTO;

import java.util.List;
import java.util.Map;

/**
 * 余票计算服务接口
 *
 * 参考 TrainRoutePairServiceImpl.fillTrainSearchResult (lines 210-277)
 *
 * 缓存结构：
 *   Key: TICKET::REMAINING::{trainId}::{date}::{seatType}
 *   Value: List<Integer> [r1, r2, ..., rn]
 *            n = 经停站数 - 1
 *            ri = 第 i 个区间的余票数
 *
 * 批量计算流程：
 *   1. 构建所有 trainId_seatType 的缓存键列表
 *   2. safeBatchLGet 批量查询
 *   3. 缓存命中 → 直接返回 List<Integer>
 *   4. 缓存未命中 → Lambda 内批量查 t_seat 获取座位总数
 *      每个区间填充相同值（t_ticket 废弃）
 *   5. 写入 Redis
 *
 * 区间查询：
 *   给定 [departureIndex, arrivalIndex)，取 List.subList 的 min()
 */
public interface TicketRemainingService {

    /**
     * 批量计算余票（核心方法）
     *
     * @param trainIdList   车次ID列表
     * @param date          乘车日期
     * @param seatTypes     座位类型列表
     * @param stationsMap   车次ID→站点列表映射
     * @return Map<"trainId_seatType", List<Integer>> 各区间余票列表
     */
    Map<String, List<Integer>> batchCalculateRemaining(
            List<Long> trainIdList,
            String date,
            List<Integer> seatTypes,
            Map<Long, List<String>> stationsMap
    );

    /**
     * 查询单个区间的余票
     *
     * @param trainId          车次ID
     * @param date             乘车日期
     * @param seatType         座位类型
     * @param departureIndex   出发站
     * @param arrivalIndex     到达站
     * @return 区间最小余票
     */
    Integer getRemainingBySegment(Long trainId,
                                 String date,
                                 Integer seatType,
                                 String departureIndex,
                                 String arrivalIndex);

    /**
     * 批量查询余票
     */
    List<TicketRemainingResultDTO> batchGetRemainingTickets(List<TicketRemainingRequestDTO> requests);
}
