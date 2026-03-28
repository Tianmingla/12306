package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 权限实体
 */
@Data
@TableName("t_permission")
public class PermissionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 权限编码
     */
    private String permissionCode;

    /**
     * 资源类型: 1-菜单, 2-按钮, 3-API
     */
    private Integer resourceType;

    /**
     * 父级ID
     */
    private Long parentId;

    /**
     * 资源路径
     */
    private String resourceUrl;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 权限描述
     */
    private String description;

    /**
     * 状态: 0-正常, 1-禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 删除标志
     */
    private Integer delFlag;
}
