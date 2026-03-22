package com.lalal.modules.user.controller;

import com.lalal.modules.result.Result;
import com.lalal.modules.user.dto.PassengerBatchRequest;
import com.lalal.modules.user.dto.PassengerVO;
import com.lalal.modules.user.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仅供微服务间调用（如 ticket-service Feign），不应对外网开放。
 */
@RestController
@RequestMapping("/api/user/internal")
public class UserInternalController {

    @Autowired
    private PassengerService passengerService;

    @PostMapping("/passengers/batch")
    public Result<List<PassengerVO>> batchPassengers(@RequestBody PassengerBatchRequest request) {
        if (request == null || request.getUserId() == null || request.getPassengerIds() == null || request.getPassengerIds().isEmpty()) {
            return Result.fail("参数错误");
        }
        try {
            return Result.success(passengerService.listByUserIdAndPassengerIdsOrdered(request.getUserId(), request.getPassengerIds()));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
