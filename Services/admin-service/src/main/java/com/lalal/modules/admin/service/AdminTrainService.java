package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.dto.PageResult;

public interface AdminTrainService {

    /**
     * 分页查询列车列表
     */
    PageResult<TrainDO> listTrains(TrainQueryRequest request);

    /**
     * 更新列车销售状态
     */
    void updateSaleStatus(Long id, Integer saleStatus);
}
