package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.CarriageDO;
import com.lalal.modules.admin.dao.SeatDO;
import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.CarriageSaveRequest;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.admin.mapper.CarriageMapper;
import com.lalal.modules.admin.mapper.SeatMapper;
import com.lalal.modules.admin.mapper.TrainMapper;
import com.lalal.modules.admin.service.AdminTrainService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminTrainServiceImpl implements AdminTrainService {

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private CarriageMapper carriageMapper;

    @Autowired
    private SeatMapper seatMapper;

    @Override
    public PageResult<TrainDO> listTrains(TrainQueryRequest request) {
        LambdaQueryWrapper<TrainDO> wrapper = new LambdaQueryWrapper<>();

        // 关键字搜索（车次号）
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            wrapper.like(TrainDO::getTrainNumber, request.getKeyword());
        }

        // 列车类型筛选
        if (request.getTrainType() != null) {
            wrapper.eq(TrainDO::getTrainType, request.getTrainType());
        }

        // 销售状态筛选
        if (request.getSaleStatus() != null) {
            wrapper.eq(TrainDO::getSaleStatus, request.getSaleStatus());
        }

        // 逻辑删除过滤
        wrapper.eq(TrainDO::getDelFlag, 0);

        // 按ID降序（最新数据在前）
        wrapper.orderByDesc(TrainDO::getId);

        // 使用 MyBatis-Plus 分页
        Page<TrainDO> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<TrainDO> result = trainMapper.selectPage(page, wrapper);

        return PageResult.ofPage(result.getRecords(), result.getTotal(), request.getPageNum(), request.getPageSize());
    }

    @Override
    public void updateSaleStatus(Long id, Integer saleStatus) {
        TrainDO train = trainMapper.selectById(id);
        if (train == null) {
            throw new RuntimeException("列车不存在");
        }

        LambdaUpdateWrapper<TrainDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TrainDO::getId, id)
                .set(TrainDO::getSaleStatus, saleStatus);
        trainMapper.update(null, wrapper);
    }

    @Override
    public List<CarriageDO> getCarriages(Long trainId) {
        LambdaQueryWrapper<CarriageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CarriageDO::getTrainId, trainId)
                .orderByAsc(CarriageDO::getCarriageNumber);
        return carriageMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCarriage(CarriageSaveRequest request) {
        // 检查车厢号是否已存在
        LambdaQueryWrapper<CarriageDO> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(CarriageDO::getTrainId, request.getTrainId())
                .eq(CarriageDO::getCarriageNumber, request.getCarriageNumber());
        if (carriageMapper.selectCount(existWrapper) > 0) {
            throw new RuntimeException("车厢号已存在");
        }

        CarriageDO carriage = new CarriageDO();
        carriage.setTrainId(request.getTrainId());
        carriage.setCarriageNumber(request.getCarriageNumber());
        carriage.setCarriageType(request.getCarriageType());
        carriage.setSeatCount(request.getSeatCount());
        carriageMapper.insert(carriage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCarriage(Long id, CarriageSaveRequest request) {
        CarriageDO carriage = carriageMapper.selectById(id);
        if (carriage == null) {
            throw new RuntimeException("车厢不存在");
        }

        LambdaUpdateWrapper<CarriageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CarriageDO::getId, id)
                .set(CarriageDO::getCarriageType, request.getCarriageType())
                .set(CarriageDO::getSeatCount, request.getSeatCount());
        carriageMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCarriage(Long id) {
        CarriageDO carriage = carriageMapper.selectById(id);
        if (carriage == null) {
            throw new RuntimeException("车厢不存在");
        }

        // 删除车厢
        carriageMapper.deleteById(id);

        // 删除该车厢的所有座位
        LambdaQueryWrapper<SeatDO> seatWrapper = new LambdaQueryWrapper<>();
        seatWrapper.eq(SeatDO::getTrainId, carriage.getTrainId())
                .eq(SeatDO::getCarriageNumber, carriage.getCarriageNumber());
        seatMapper.delete(seatWrapper);
    }

    @Override
    public List<SeatDO> getSeats(Long trainId, String carriageNumber) {
        LambdaQueryWrapper<SeatDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .orderByAsc(SeatDO::getSeatNumber);
        return seatMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeatType(Long seatId, Integer seatType) {
        SeatDO seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new RuntimeException("座位不存在");
        }

        LambdaUpdateWrapper<SeatDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SeatDO::getId, seatId)
                .set(SeatDO::getSeatType, seatType);
        seatMapper.update(null, wrapper);
    }
}
