package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQueryNormal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StationQueryRequest extends PageQueryNormal {

    /**
     * 地区筛选
     */
    private String region;
}
