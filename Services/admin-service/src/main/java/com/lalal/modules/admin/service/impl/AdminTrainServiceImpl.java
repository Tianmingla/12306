package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.admin.mapper.TrainMapper;
import com.lalal.modules.admin.service.AdminTrainService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminTrainServiceImpl implements AdminTrainService {

    @Autowired
    private TrainMapper trainMapper;

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
}
