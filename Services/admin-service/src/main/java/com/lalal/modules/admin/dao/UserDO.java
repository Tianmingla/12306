package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户实体（只读，用于管理后台查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class UserDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;
}
