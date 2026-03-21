package com.lalal.modules.user.dao;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_user")
public class UserDO extends BaseDO {

    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 证件类型 (例如: 1-身份证, 2-护照等)
     */
    private Integer idCardType;

    /**
     * 证件号码
     */
    private String idCardNumber;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 旅客类型 (例如: 1-成人, 2-儿童, 3-学生, 4-残疾军人)
     */
    private Integer passengerType;
}
