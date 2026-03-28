package com.lalal.modules.admin.dto;

import lombok.Data;

/**
 * 车厢保存请求
 */
@Data
public class CarriageSaveRequest {

    /**
     * 列车ID
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
}
