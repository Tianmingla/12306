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

/**
 * 站间距离实体
 */
@Data
@TableName("t_station_distance")
public class StationDistanceDO extends BaseDO {

    /**
     * id
     */
    private Long id;

    /**
     * 列车ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 出发站ID
     */
    private Long departureStationId;

    /**
     * 出发站名
     */
    private String departureStationName;

    /**
     * 到达站ID
     */
    private Long arrivalStationId;

    /**
     * 到达站名
     */
    private String arrivalStationName;

    /**
     * 里程(公里)
     */
    private Integer distance;

    /**
     * 线路名称
     */
    private String lineName;
}
