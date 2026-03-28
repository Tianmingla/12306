package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.PassengerDO;
import com.lalal.modules.admin.dao.UserDO;
import com.lalal.modules.admin.dto.UserQueryRequest;
import com.lalal.modules.admin.service.AdminUserService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    /**
     * 分页查询用户列表
     */
    @GetMapping("/list")
    public Result<PageResult<UserDO>> listUsers(UserQueryRequest request) {
        return Result.success(adminUserService.listUsers(request));
    }

    /**
     * 获取用户的乘车人列表
     */
    @GetMapping("/{userId}/passengers")
    public Result<List<PassengerDO>> listPassengers(@PathVariable Long userId) {
        return Result.success(adminUserService.listPassengersByUserId(userId));
    }

    /**
     * 切换用户状态
     */
    @PutMapping("/{id}/status")
    public Result<String> toggleStatus(@PathVariable Long id) {
        try {
            adminUserService.toggleUserStatus(id);
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }
}
