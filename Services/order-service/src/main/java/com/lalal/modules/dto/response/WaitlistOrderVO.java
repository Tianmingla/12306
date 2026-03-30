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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalal.modules.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 候补订单响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistOrderVO {

    /**
     * 候补订单ID
     */
    private Long id;

    /**
     * 候补订单号
     */
    private String waitlistSn;

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
     * 座位类型描述
     */
    private String seatTypesText;

    /**
     * 预支付金额
     */
    private BigDecimal prepayAmount;

    /**
     * 截止时间
     */
    private Date deadline;

    /**
     * 状态：0=待兑现，1=兑现中，2=已兑现，3=已取消，4=已过期
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusText;

    /**
     * 兑现成功后的订单号
     */
    private String fulfilledOrderSn;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 排队人数（预估）
     */
    private Integer queuePosition;

    /**
     * 预计成功率
     */
    private Integer successRate;
}
