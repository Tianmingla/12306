package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.CarriageDO;
import com.lalal.modules.admin.dao.SeatDO;
import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.CarriageSaveRequest;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.dto.PageResult;

import java.util.List;

public interface AdminTrainService {

    /**
     * 分页查询列车列表
     */
    PageResult<TrainDO> listTrains(TrainQueryRequest request);

    /**
     * 更新列车销售状态
     */
    void updateSaleStatus(Long id, Integer saleStatus);

    /**
     * 获取列车车厢列表
     */
    List<CarriageDO> getCarriages(Long trainId);

    /**
     * 添加车厢
     */
    void addCarriage(CarriageSaveRequest request);

    /**
     * 更新车厢
     */
    void updateCarriage(Long id, CarriageSaveRequest request);

    /**
     * 删除车厢
     */
    void deleteCarriage(Long id);

    /**
     * 获取车厢座位列表
     */
    List<SeatDO> getSeats(Long trainId, String carriageNumber);

    /**
     * 更新座位类型
     */
    void updateSeatType(Long seatId, Integer seatType);
}
