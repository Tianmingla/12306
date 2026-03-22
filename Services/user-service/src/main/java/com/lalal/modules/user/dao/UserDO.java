package com.lalal.modules.user.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

@Data
@TableName("t_user")
public class UserDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录手机号（唯一）
     */
    private String phone;

    /**
     * 邮箱（可选）
     */
    private String email;
}
