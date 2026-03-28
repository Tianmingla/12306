package com.lalal.modules.admin.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色详情响应
 */
@Data
public class RoleDetailResponse {

    /**
     * 角色ID
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
     * 状态
     */
    private Integer status;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;

    /**
     * 权限列表
     */
    private List<PermissionVO> permissions;

    @Data
    public static class PermissionVO {
        private Long id;
        private String permissionName;
        private String permissionCode;
        private Integer resourceType;
        private Long parentId;
    }
}
