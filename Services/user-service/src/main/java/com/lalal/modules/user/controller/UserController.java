package com.lalal.modules.user.controller;

import com.lalal.modules.result.Result;
import com.lalal.modules.user.dao.UserDO;
import com.lalal.modules.user.dto.LoginRequest;
import com.lalal.modules.user.dto.RegisterRequest;
import com.lalal.modules.user.service.UserService;
import com.lalal.modules.user.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService; // 注入新的 Service

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest loginRequest) {
        // 调用 Service 层进行真实验证
        UserDO user = userService.login(loginRequest);

        if (user != null) {
            String token = jwtUtils.generateToken(user.getUsername());
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            data.put("username", user.getUsername());
            return Result.success(data);
        }
        return Result.fail("Invalid username or password");
    }

    // 新增注册接口
    @PostMapping("/register")
    public Result<String> register(@RequestBody RegisterRequest registerRequest) {
        try {
            boolean success = userService.register(registerRequest);
            if (success) {
                return Result.success("Registration successful");
            } else {
                return Result.fail("Registration failed");
            }
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Map<String, String>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.getUsernameFromToken(token);
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("username", username);
                userInfo.put("role", "USER");
                return Result.success(userInfo);
            }
        }
        return Result.fail("Unauthorized");
    }
}