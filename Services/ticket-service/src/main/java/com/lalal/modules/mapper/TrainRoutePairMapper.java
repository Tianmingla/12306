package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.StationDO;
import com.lalal.modules.entity.TrainRoutePairDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
@Mapper
public interface TrainRoutePairMapper extends BaseMapper<TrainRoutePairDO> {
    /**
     * 根据站台列表搜索车次
     */
    List<TrainRoutePairDO> searchTrainsByStationList(List<StationDO> start,List<StationDO> end);
}
