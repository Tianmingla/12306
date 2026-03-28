package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQueryNormal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrainQueryRequest extends PageQueryNormal {

    /**
     * 列车类型
     */
    private Integer trainType;

    /**
     * 销售状态
     */
    private Integer saleStatus;
}
