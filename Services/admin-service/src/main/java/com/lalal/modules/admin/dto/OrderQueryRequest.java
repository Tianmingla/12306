package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryRequest extends PageQuery {

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 订单编号
     */
    private String orderSn;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;
}
