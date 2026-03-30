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

package com.lalal.modules.controller;

import com.lalal.modules.dto.response.StationScreenResponseDTO;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.StationScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 车站大屏控制器
 *
 * 提供车站候车大厅大屏展示所需的 API 接口
 */
@RestController
@RequestMapping("/api/ticket/screen")
@RequiredArgsConstructor
public class StationScreenController {

    private final StationScreenService stationScreenService;

    /**
     * 获取车站大屏信息
     *
     * @param stationName 车站名称
     * @return 车站大屏数据
     */
    @GetMapping("/station/{stationName}")
    public Result<StationScreenResponseDTO> getStationScreen(
            @PathVariable String stationName) {
        StationScreenResponseDTO response = stationScreenService.getStationScreen(stationName);
        if (response == null) {
            return Result.fail("车站不存在", 404);
        }
        return Result.success(response);
    }

    /**
     * 根据车站 ID 获取大屏信息
     *
     * @param stationId 车站 ID
     * @return 车站大屏数据
     */
    @GetMapping("/station/id/{stationId}")
    public Result<StationScreenResponseDTO> getStationScreenById(
            @PathVariable Long stationId) {
        StationScreenResponseDTO response = stationScreenService.getStationScreenById(stationId);
        if (response == null) {
            return Result.fail("车站不存在", 404);
        }
        return Result.success(response);
    }
}
