/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 候补购票实体
 *
 * 当所需车次无票时，用户可以提交候补订单，
 * 一旦有票放出，系统自动为用户购票
 */
@Data
@TableName("t_waitlist_order")
public class WaitlistOrderDO extends BaseDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 候补订单号
     */
    private String waitlistSn;

    /**
     * 用户账号（手机号）
     */
    private String username;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 乘车日期
     */
    private Date travelDate;

    /**
     * 座位类型（可多选，逗号分隔）
     */
    private String seatTypes;

    /**
     * 乘车人ID列表（逗号分隔）
     */
    private String passengerIds;

    /**
     * 预支付金额（用于自动扣款）
     */
    private BigDecimal prepayAmount;

    /**
     * 截止时间（超过此时间未购票成功则取消）
     */
    private Date deadline;

    /**
     * 状态：0=待兑现，1=兑现中，2=已兑现，3=已取消，4=已过期
     */
    private Integer status;

    /**
     * 兑现成功后的订单号
     */
    private String fulfilledOrderSn;

    /**
     * 备注
     */
    private String remark;
}
