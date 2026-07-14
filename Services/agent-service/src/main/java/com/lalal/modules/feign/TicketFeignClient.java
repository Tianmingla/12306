package com.lalal.modules.feign;

import com.lalal.modules.dto.FeignResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 车票服务 Feign 客户端
 */
@FeignClient(name = "ticket-service")
public interface TicketFeignClient {

    /**
     * 搜索车次（直达）
     */
    @GetMapping("/api/ticket/search")
    FeignResult searchTrains(@RequestParam("from") String from,
                             @RequestParam("to") String to,
                             @RequestParam("date") String date);

    /**
     * 换乘搜索
     */
    @GetMapping("/api/ticket/transfer")
    FeignResult searchTransfer(@RequestParam("from") String from,
                               @RequestParam("to") String to,
                               @RequestParam("date") String date);

    /**
     * 查询车次经停站
     */
    @GetMapping("/api/trainDetail/stations")
    FeignResult getTrainStations(@RequestParam("trainNum") String trainNum);
}
