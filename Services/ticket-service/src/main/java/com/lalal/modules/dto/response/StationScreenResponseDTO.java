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

import java.util.List;

/**
 * 车站大屏响应 DTO
 *
 * 包含车站基本信息和当前时刻所有待发列车列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationScreenResponseDTO {

    /**
     * 车站ID
     */
    private Long stationId;

    /**
     * 车站名称
     */
    private String stationName;

    /**
     * 车站编码
     */
    private String stationCode;

    /**
     * 当前时间（服务端时间）
     */
    private String currentTime;

    /**
     * 当前日期
     */
    private String currentDate;

    /**
     * 当日发车总数
     */
    private Integer totalTrainsToday;

    /**
     * 正点率（百分比）
     */
    private Double onTimeRate;

    /**
     * 待发列车列表（按发车时间排序）
     */
    private List<StationScreenTrainDTO> trains;

    /**
     * 公告信息（可选）
     */
    private List<String> announcements;
}
