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

import java.util.Date;

/**
 * 车站大屏列车信息 DTO
 *
 * 用于展示车站候车大厅大屏上的列车实时信息，包括：
 * - 正晚点状态
 * - 检票状态
 * - 候车室/站台信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationScreenTrainDTO {

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 列车类型：0=高铁，1=动车，2=普速
     */
    private Integer trainType;

    /**
     * 列车类型名称
     */
    private String trainTypeName;

    /**
     * 终到站
     */
    private String terminalStation;

    /**
     * 发车时间（格式：HH:mm）
     */
    private String departureTime;

    /**
     * 预计发车时间（晚点时使用，格式：HH:mm）
     */
    private String estimatedDepartureTime;

    /**
     * 正晚点状态：0=正点，1=晚点，2=待定
     */
    private Integer delayStatus;

    /**
     * 正晚点状态描述
     */
    private String delayStatusText;

    /**
     * 晚点分钟数（正点时为0）
     */
    private Integer delayMinutes;

    /**
     * 检票状态：0=未开始，1=检票中，2=已停止，3=已开车
     */
    private Integer checkInStatus;

    /**
     * 检票状态描述
     */
    private String checkInStatusText;

    /**
     * 候车室
     */
    private String waitingRoom;

    /**
     * 检票口
     */
    private String checkInGate;

    /**
     * 站台
     */
    private String platform;

    /**
     * 剩余时间描述（如"约15分钟后检票"）
     */
    private String remainingTimeDesc;

    /**
     * 实际发车时间（用于计算剩余时间）
     */
    private Date actualDepartureTime;
}
