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

package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 车票实体
 * 数据库设计没有严格遵循原子性设计 而是包含有些冗余字段
 * 这是为了保证高性能 牺牲一部分空间减少连表操作 达到更高的一个性能
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ticket")
public class TicketDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 列车id
     */
    private Long trainId;

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 座位号
     */
    private String seatNumber;

    /**
     * 冗余字段 座位类型
     */
    private Integer seatType;
    /**
     * 乘车人 ID
     */
    private String passengerId;

    /**
    * 乘车出发时间
    * */
    private Date travelDate;

    /**
     * 出发站点
     */
    private String departureStation;

    /**
     * 到达站点
     */
    private String arrivalStation;

    /**
     * 车票状态
     */
    private Integer ticketStatus;
}
