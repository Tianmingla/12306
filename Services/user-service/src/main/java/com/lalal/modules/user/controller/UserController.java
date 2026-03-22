package com.lalal.modules.user.controller;

import com.lalal.modules.result.Result;
import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.LoginRequest;
import com.lalal.modules.user.dto.PassengerSaveRequest;
import com.lalal.modules.user.dto.PassengerVO;
import com.lalal.modules.user.dto.SendSmsRequest;
import com.lalal.modules.user.service.PassengerService;
import com.lalal.modules.user.service.SmsCodeService;
import com.lalal.modules.user.service.UserService;
import com.lalal.modules.user.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UserService userService;
    @Autowired
    private SmsCodeService smsCodeService;
    @Autowired
    private PassengerService passengerService;

    /**
     * 发送登录短信验证码
     */
    @PostMapping("/sms/send")
    public Result<String> sendLoginSms(@RequestBody SendSmsRequest request) {
        try {
            if (request == null) {
                return Result.fail("请求体不能为空");
            }
            smsCodeService.sendLoginCode(request.getPhone());
            return Result.success("验证码已发送");
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest loginRequest) {
        UserDO user = userService.loginBySms(loginRequest);
        if (user == null) {
            return Result.fail("手机号或验证码错误");
        }
        String token = jwtUtils.generateToken(user.getPhone(), user.getId());
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("phone", user.getPhone());
        data.put("userId", String.valueOf(user.getId()));
        return Result.success(data);
    }

    @GetMapping("/info")
    public Result<Map<String, String>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized");
        }
        UserDO user = userService.findById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("phone", user.getPhone());
        userInfo.put("userId", String.valueOf(user.getId()));
        userInfo.put("role", "USER");
        return Result.success(userInfo);
    }

    @GetMapping("/passengers")
    public Result<List<PassengerVO>> listPassengers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized");
        }
        return Result.success(passengerService.listByUserId(userId));
    }

    @PostMapping("/passengers")
    public Result<Long> addPassenger(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                     @RequestBody PassengerSaveRequest request) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized");
        }
        try {
            return Result.success(passengerService.addPassenger(userId, request));
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/passengers/{id}")
    public Result<String> updatePassenger(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                          @PathVariable("id") Long passengerId,
                                          @RequestBody PassengerSaveRequest request) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized");
        }
        try {
            passengerService.updatePassenger(userId, passengerId, request);
            return Result.success("ok");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/passengers/{id}")
    public Result<String> deletePassenger(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                         @PathVariable("id") Long passengerId) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized");
        }
        try {
            passengerService.deletePassenger(userId, passengerId);
            return Result.success("ok");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return null;
        }
        return jwtUtils.getUserIdFromToken(token);
    }
}
