package com.lalal.modules.user.controller;

import com.lalal.modules.result.Result;
import com.lalal.modules.user.dto.LoginRequest;
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

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody LoginRequest loginRequest) {
        // TODO: Implement real authentication logic with database
        // Mock authentication for now
        if ("admin".equals(loginRequest.getUsername()) && "123456".equals(loginRequest.getPassword())) {
            String token = jwtUtils.generateToken(loginRequest.getUsername());
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            return Result.success(data);
        }
        return Result.fail("Invalid username or password");
    }

    @GetMapping("/info")
    public Result<Map<String, String>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        // TODO: Fetch user info from database
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
