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

package com.lalal.modules.service;

import com.lalal.modules.dto.response.StationScreenResponseDTO;

/**
 * 车站大屏服务接口
 *
 * 提供车站候车大厅大屏展示所需的实时列车信息
 */
public interface StationScreenService {

    /**
     * 获取车站大屏信息
     *
     * @param stationName 车站名称
     * @return 车站大屏信息（包含待发列车列表）
     */
    StationScreenResponseDTO getStationScreen(String stationName);

    /**
     * 获取车站大屏信息（根据车站ID）
     *
     * @param stationId 车站ID
     * @return 车站大屏信息
     */
    StationScreenResponseDTO getStationScreenById(Long stationId);
}
