package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 乘车人实体（只读，用于管理后台查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_passenger")
public class PassengerDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 证件类型
     */
    private Integer idCardType;

    /**
     * 证件号码
     */
    private String idCardNumber;

    /**
     * 乘客类型
     */
    private Integer passengerType;

    /**
     * 手机号
     */
    private String phone;
}
