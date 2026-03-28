package com.lalal.modules.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色保存请求
 */
@Data
public class RoleSaveRequest {

    /**
     * 角色ID（编辑时需要）
     */
    private Long id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;
}
