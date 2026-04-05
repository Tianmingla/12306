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

import java.time.LocalDate;

/**
 * 列车票价上浮配置实体
 */
@Data
@TableName("t_train_fare_config")
public class TrainFareConfigDO extends BaseDO {

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
     * 上浮类型: 0-普通车, 1-新空调50%, 2-新空调一档40%, 3-新空调二档30%, 4-高软180%, 5-高软208%
     */
    private Integer surchargeType;

    /**
     * 是否春运期间
     */
    private Integer isPeakSeason;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expireDate;
}
