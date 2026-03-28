package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.PermissionDO;
import com.lalal.modules.admin.dao.RoleDO;
import com.lalal.modules.admin.dto.RoleDetailResponse;
import com.lalal.modules.admin.dto.RoleQueryRequest;
import com.lalal.modules.admin.dto.RoleSaveRequest;
import com.lalal.modules.admin.service.AdminRoleService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 */
@RestController
@RequestMapping("/api/admin/role")
public class AdminRoleController {

    @Autowired
    private AdminRoleService adminRoleService;

    /**
     * 分页查询角色列表
     */
    @GetMapping("/list")
    public Result<PageResult<RoleDO>> listRoles(RoleQueryRequest request) {
        return Result.success(adminRoleService.listRoles(request));
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{id}")
    public Result<RoleDetailResponse> getRoleDetail(@PathVariable Long id) {
        return Result.success(adminRoleService.getRoleDetail(id));
    }

    /**
     * 创建角色
     */
    @PostMapping
    public Result<String> createRole(@RequestBody RoleSaveRequest request) {
        try {
            adminRoleService.createRole(request);
            return Result.success("创建成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    public Result<String> updateRole(@PathVariable Long id, @RequestBody RoleSaveRequest request) {
        try {
            adminRoleService.updateRole(id, request);
            return Result.success("更新成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteRole(@PathVariable Long id) {
        try {
            adminRoleService.deleteRole(id);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 更新角色状态
     */
    @PutMapping("/{id}/status")
    public Result<String> updateRoleStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        try {
            adminRoleService.updateRoleStatus(id, request.getStatus());
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 获取所有权限列表
     */
    @GetMapping("/permissions")
    public Result<List<PermissionDO>> getAllPermissions() {
        return Result.success(adminRoleService.getAllPermissions());
    }

    /**
     * 获取角色的权限ID列表
     */
    @GetMapping("/{id}/permissions")
    public Result<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        return Result.success(adminRoleService.getRolePermissionIds(id));
    }

    /**
     * 分配角色权限
     */
    @PostMapping("/{id}/permissions")
    public Result<String> assignPermissions(@PathVariable Long id, @RequestBody PermissionRequest request) {
        try {
            adminRoleService.assignPermissions(id, request.getPermissionIds());
            return Result.success("分配成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 状态请求
     */
    @lombok.Data
    public static class StatusRequest {
        private Integer status;
    }

    /**
     * 权限请求
     */
    @lombok.Data
    public static class PermissionRequest {
        private List<Long> permissionIds;
    }
}
