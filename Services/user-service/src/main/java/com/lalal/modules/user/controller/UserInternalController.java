package com.lalal.modules.user.controller;

import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.PassengerBatchRequest;
import com.lalal.modules.user.dto.PassengerVO;
import com.lalal.modules.user.service.PassengerService;
import com.lalal.modules.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仅供微服务间调用（如 ticket-service Feign），不应对外网开放。
 */
@RestController
@RequestMapping("/api/user/internal")
public class UserInternalController {

    @Autowired
    private PassengerService passengerService;
    @Autowired
    private UserService userService;

    @PostMapping("/passengers/batch")
    public Result<List<PassengerVO>> batchPassengers(@RequestBody PassengerBatchRequest request) {
        if (request == null || request.getUserId() == null || request.getPassengerIds() == null || request.getPassengerIds().isEmpty()) {
            return Result.fail("参数错误", ReturnCode.fail.code());
        }
        try {
            return Result.success(passengerService.listByUserIdAndPassengerIdsOrdered(request.getUserId(), request.getPassengerIds()));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage(),ReturnCode.fail.code());
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(),ReturnCode.fail.code());
        }
    }

    /**
     * 根据手机号查询用户ID（供其他微服务调用）
     */
    @PostMapping("/resolve-user-id")
    public Result<Map<String, Object>> resolveUserId(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isBlank()) {
            return Result.fail("手机号不能为空", ReturnCode.fail.code());
        }
        UserDO user = userService.findByPhone(phone);
        if (user == null) {
            return Result.fail("用户不存在", ReturnCode.fail.code());
        }
        return Result.success(Map.of("userId", user.getId(), "phone", user.getPhone()));
    }
}
