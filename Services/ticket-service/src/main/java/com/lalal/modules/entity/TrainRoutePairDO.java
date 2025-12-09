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
import lombok.Data;

import java.util.Date;

/**
 * 预计算实体
 * 数据库设计没有严格遵循原子性设计 而是包含有些冗余字段
 * 这是为了保证高性能 牺牲一部分空间减少连表操作 达到更高的一个性能
 * TODO 价格计算
 */
@Data
@TableName("t_train_route_pair")
public class TrainRoutePairDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 车次id
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;
    /**
     * 出发站点
     */
    private String departureStation;

    /**
     * 到达站点
     */
    private String arrivalStation;

    /**
     * 出发时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 起始城市
     */
    private String startRegion;

    /**
     * 终点城市
     */
    private String endRegion;

}
