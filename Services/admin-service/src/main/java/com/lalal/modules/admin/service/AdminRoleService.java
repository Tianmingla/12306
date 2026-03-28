package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.PermissionDO;
import com.lalal.modules.admin.dao.RoleDO;
import com.lalal.modules.admin.dto.RoleDetailResponse;
import com.lalal.modules.admin.dto.RoleQueryRequest;
import com.lalal.modules.admin.dto.RoleSaveRequest;
import com.lalal.modules.dto.PageResult;

import java.util.List;

/**
 * 角色管理服务接口
 */
public interface AdminRoleService {

    /**
     * 分页查询角色列表
     */
    PageResult<RoleDO> listRoles(RoleQueryRequest request);

    /**
     * 获取角色详情
     */
    RoleDetailResponse getRoleDetail(Long id);

    /**
     * 创建角色
     */
    void createRole(RoleSaveRequest request);

    /**
     * 更新角色
     */
    void updateRole(Long id, RoleSaveRequest request);

    /**
     * 删除角色
     */
    void deleteRole(Long id);

    /**
     * 更新角色状态
     */
    void updateRoleStatus(Long id, Integer status);

    /**
     * 获取所有权限列表
     */
    List<PermissionDO> getAllPermissions();

    /**
     * 获取角色的权限ID列表
     */
    List<Long> getRolePermissionIds(Long roleId);

    /**
     * 分配角色权限
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
