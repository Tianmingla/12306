package com.lalal.modules.tool;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.dto.FeignResult;
import com.lalal.modules.feign.TicketFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 车次搜索工具
 * 提供直达车次搜索、换乘推荐、车次经停站查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainSearchTool {

    private final TicketFeignClient ticketFeignClient;
    private final ToolRegistry toolRegistry;

    /**
     * 初始化时自动注册到工具注册中心
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        toolRegistry.register(this);
    }

    @Tool(description = "搜索直达车次。根据出发城市、到达城市和日期查询所有可用的直达列车信息，包括车次号、出发/到达时间、各座位类型余票和票价。")
    public String searchDirectTrains(
            @ToolParam(description = "出发城市名，如：北京、上海") String from,
            @ToolParam(description = "到达城市名，如：广州、深圳") String to,
            @ToolParam(description = "乘车日期，格式：yyyy-MM-dd，如：2026-07-15") String date) {
        log.info("Tool: searchDirectTrains from={}, to={}, date={}", from, to, date);

        // 参数校验
        if (from == null || from.isBlank() || to == null || to.isBlank() || date == null || date.isBlank()) {
            return "参数不完整，请提供出发城市、到达城市和乘车日期";
        }

        try {
            FeignResult result = ticketFeignClient.searchTrains(from, to, date);
            if (result.isSuccess()) {
                return formatTrainResult(result);
            } else {
                return "搜索车次失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("searchDirectTrains error", e);
            return "搜索车次时发生错误，请稍后重试";
        }
    }

    @Tool(description = "搜索换乘方案。当直达车次无票或用户需要换乘时，搜索从出发城市到到达城市的换乘方案，支持Dijkstra和A*算法。")
    public String searchTransferTrains(
            @ToolParam(description = "出发城市名") String from,
            @ToolParam(description = "到达城市名") String to,
            @ToolParam(description = "乘车日期，格式：yyyy-MM-dd") String date) {
        log.info("Tool: searchTransferTrains from={}, to={}, date={}", from, to, date);

        if (from == null || from.isBlank() || to == null || to.isBlank() || date == null || date.isBlank()) {
            return "参数不完整，请提供出发城市、到达城市和乘车日期";
        }

        try {
            FeignResult result = ticketFeignClient.searchTransfer(from, to, date);
            if (result.isSuccess()) {
                return formatTransferResult(result);
            } else {
                return "搜索换乘方案失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("searchTransferTrains error", e);
            return "搜索换乘方案时发生错误，请稍后重试";
        }
    }

    @Tool(description = "查询车次经停站信息。根据车次号查询该车次经过的所有站点、到达和出发时间。")
    public String getTrainStationDetails(
            @ToolParam(description = "车次号，如：G1、D2316") String trainNum) {
        log.info("Tool: getTrainStationDetails trainNum={}", trainNum);

        if (trainNum == null || trainNum.isBlank()) {
            return "请提供车次号";
        }

        try {
            FeignResult result = ticketFeignClient.getTrainStations(trainNum);
            if (result.isSuccess()) {
                return JSON.toJSONString(result.getData());
            } else {
                return "查询车次经停站失败：" + result.getMessage();
            }
        } catch (Exception e) {
            log.error("getTrainStationDetails error", e);
            return "查询车次经停站时发生错误";
        }
    }

    private String formatTrainResult(FeignResult result) {
        return JSON.toJSONString(result.getData());
    }

    private String formatTransferResult(FeignResult result) {
        return JSON.toJSONString(result.getData());
    }
}
