package com.lalal.modules.controller;

import com.lalal.modules.dto.transfer.TransferRouteResult;
import com.lalal.modules.dto.transfer.TransferSearchRequest;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.TransferSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 换乘搜索控制器
 *
 * 提供智能中转/换乘路线搜索功能
 */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TransferSearchController {

    private final TransferSearchService transferSearchService;

    /**
     * 智能换乘搜索
     *
     * 支持两种算法：Dijkstra（精确搜索）/ AStar（启发式搜索）
     *
     * @param from     出发站
     * @param to       目的站
     * @param date     乘车日期 yyyy-MM-dd
     * @param algorithm 搜索算法：Dijkstra / AStar（默认 AStar）
     * @param limit    返回方案数量（默认10）
     * @return 换乘方案列表
     */
    @GetMapping("/transfer")
    public Result<List<TransferRouteResult>> searchTransfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String date,
            @RequestParam(defaultValue = "AStar") String algorithm,
            @RequestParam(defaultValue = "10") int limit) {

        TransferSearchRequest request = TransferSearchRequest.builder()
                .from(from)
                .to(to)
                .date(date)
                .algorithm(algorithm)
                .limit(limit)
                .build();

        List<TransferRouteResult> results = transferSearchService.search(request);
        return Result.success(results);
    }

    /**
     * 使用 Dijkstra 算法搜索换乘路线
     */
    @GetMapping("/transfer/dijkstra")
    public Result<List<TransferRouteResult>> searchByDijkstra(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String date) {

        List<TransferRouteResult> results = transferSearchService.searchByDijkstra(from, to, date);
        return Result.success(results);
    }

    /**
     * 使用 A* 算法搜索换乘路线
     */
    @GetMapping("/transfer/astar")
    public Result<List<TransferRouteResult>> searchByAStar(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String date) {

        List<TransferRouteResult> results = transferSearchService.searchByAStar(from, to, date);
        return Result.success(results);
    }

    /**
     * 高级换乘搜索（带更多参数）
     */
    @PostMapping("/transfer/advanced")
    public Result<List<TransferRouteResult>> searchTransferAdvanced(
            @RequestBody TransferSearchRequest request) {

        List<TransferRouteResult> results = transferSearchService.search(request);
        return Result.success(results);
    }
}
