package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQueryNormal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RouteQueryRequest extends PageQueryNormal {

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 车站名称
     */
    private String stationName;
}
