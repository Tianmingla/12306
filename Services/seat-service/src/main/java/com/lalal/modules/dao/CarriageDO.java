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

package com.lalal.modules.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import com.lalal.modules.core.tool.CarriageUtils;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.model.Carriage;
import lombok.Data;

import java.util.Arrays;


/**
 * 车厢实体
 * 
 */
@Data
@TableName("t_carriage")
public class CarriageDO extends BaseDO {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 列车id
     */
    private Long trainId;

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 车厢类型
     */
    private Integer carriageType;

    /**
     * 座位数
     */
    private Integer seatCount;

    public Carriage toCarriage(){
        Carriage carriage=new Carriage();
        carriage.setCarNumber(carriageNumber);
        SeatType seatType=SeatType.findByCode(carriageType);
        carriage.setSeatType(seatType);
        carriage.setIndex(Integer.parseInt(carriageNumber));
        carriage.setLayout(CarriageUtils.getLayout(seatType));
        return carriage;
    }
}
