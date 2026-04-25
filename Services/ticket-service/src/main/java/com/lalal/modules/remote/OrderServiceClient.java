package com.lalal.modules.remote;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@FeignClient(name = "order-service")
@Service
public interface OrderServiceClient {
    @PostMapping("/api/order/create")
    String create(@RequestBody OrderCreateRemoteRequestDTO request);

    @Data
    class OrderCreateRemoteRequestDTO {
        private String trainNumber;
        private String startStation;
        private String endStation;
        private String username;
        private LocalDate runDate;
        /**
         * 计划发车时间（毫秒时间戳）
         */
        private Long planDepartTime;
        /**
         * 计划到达时间（毫秒时间戳）
         */
        private Long planArrivalTime;
        private List<OrderItemRemoteRequestDTO> items;

        @Data
        public static class OrderItemRemoteRequestDTO {
            private Long passengerId;
            private String carriageNumber;
            private String seatNumber;
            private Integer seatType;
            private BigDecimal amount;
            private String realName;
            private String idCard;
        }
    }
}
