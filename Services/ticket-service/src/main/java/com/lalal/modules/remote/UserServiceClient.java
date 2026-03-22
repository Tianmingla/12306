package com.lalal.modules.remote;

import com.lalal.modules.result.Result;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service", contextId = "userServiceClient")
@Service
public interface UserServiceClient {

    @PostMapping("/api/user/internal/passengers/batch")
    Result<List<PassengerRemoteVO>> batchPassengers(@RequestBody PassengersBatchRequest request);

    @Data
    class PassengersBatchRequest {
        private Long userId;
        private List<Long> passengerIds;
    }

    @Data
    class PassengerRemoteVO {
        private Long id;
        private String realName;
        private Integer idCardType;
        private String idCardNumber;
        private Integer passengerType;
        private String phone;
    }
}
