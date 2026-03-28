package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.StationDO;
import com.lalal.modules.admin.dto.StationQueryRequest;
import com.lalal.modules.dto.PageResult;

import java.util.List;

public interface AdminStationService {

    /**
     * 分页查询车站列表
     */
    PageResult<StationDO> listStations(StationQueryRequest request);

    /**
     * 获取所有车站（下拉选择用）
     */
    List<StationDO> listAllStations();
}
