package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.StationDO;
import com.lalal.modules.admin.dto.StationQueryRequest;
import com.lalal.modules.admin.mapper.StationMapper;
import com.lalal.modules.admin.service.AdminStationService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminStationServiceImpl implements AdminStationService {

    @Autowired
    private StationMapper stationMapper;

    @Override
    public PageResult<StationDO> listStations(StationQueryRequest request) {
        LambdaQueryWrapper<StationDO> wrapper = new LambdaQueryWrapper<>();

        // 关键字搜索（车站名称/代码/拼音）
        if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
            wrapper.and(w -> w
                    .like(StationDO::getName, request.getKeyword())
                    .or()
                    .like(StationDO::getCode, request.getKeyword())
                    .or()
                    .like(StationDO::getSpell, request.getKeyword())
            );
        }

        // 地区筛选
        if (request.getRegion() != null && !request.getRegion().isEmpty()) {
            wrapper.eq(StationDO::getRegion, request.getRegion());
        }

        // 逻辑删除过滤
        wrapper.eq(StationDO::getDelFlag, 0);

        // 按ID降序
        wrapper.orderByDesc(StationDO::getId);

        // 使用 MyBatis-Plus 分页
        Page<StationDO> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<StationDO> result = stationMapper.selectPage(page, wrapper);

        return PageResult.ofPage(result.getRecords(), result.getTotal(), request.getPageNum(), request.getPageSize());
    }

    @Override
    public List<StationDO> listAllStations() {
        LambdaQueryWrapper<StationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StationDO::getDelFlag, 0)
                .orderByAsc(StationDO::getName);
        return stationMapper.selectList(wrapper);
    }
}
