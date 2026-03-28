package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.AdminUserDO;
import com.lalal.modules.admin.dto.AdminLoginRequest;
import com.lalal.modules.admin.dto.AdminLoginResponse;
import com.lalal.modules.admin.service.AdminAuthService;
import com.lalal.modules.admin.utils.AdminJwtUtils;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private AdminJwtUtils adminJwtUtils;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        try {
            AdminLoginResponse response = adminAuthService.login(request);
            return Result.success(response);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.failedAuthorized.code());
        }
    }

    /**
     * 获取当前登录管理员信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(authHeader);
        if (userId == null) {
            return Result.fail("Unauthorized",ReturnCode.unauthorized.code());
        }

        AdminUserDO adminUser = adminAuthService.findById(userId);
        if (adminUser == null) {
            return Result.fail("用户不存在",ReturnCode.failedAuthorized.code());
        }

        Map<String, Object> info = new HashMap<>();
        info.put("userId", adminUser.getId());
        info.put("username", adminUser.getUsername());
        info.put("realName", adminUser.getRealName());
        info.put("role", adminUser.getRole());
        info.put("type", "ADMIN");

        return Result.success(info);
    }

    private Long resolveUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        if (!adminJwtUtils.validateToken(token)) {
            return null;
        }
        return adminJwtUtils.getUserIdFromToken(token);
    }
}
