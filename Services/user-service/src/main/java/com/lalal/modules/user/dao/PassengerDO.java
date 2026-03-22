package com.lalal.modules.user.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

@Data
@TableName("t_passenger")
public class PassengerDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属账号用户 ID（t_user.id）
     */
    private Long userId;

    private String realName;

    /**
     * 证件类型（1 身份证 等，与业务约定一致）
     */
    private Integer idCardType;

    private String idCardNumber;

    /**
     * 旅客类型（1 成人 2 儿童 3 学生 等）
     */
    private Integer passengerType;

    /**
     * 乘车人联系手机（可选）
     */
    private String phone;
}
