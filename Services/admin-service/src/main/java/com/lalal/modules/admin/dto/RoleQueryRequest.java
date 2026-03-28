package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQueryNormal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleQueryRequest extends PageQueryNormal {

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 状态
     */
    private Integer status;
}
