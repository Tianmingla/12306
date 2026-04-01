package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.StationDO;
import com.lalal.modules.entity.TrainRoutePairDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@Mapper
public interface TrainRoutePairMapper extends BaseMapper<TrainRoutePairDO> {
    /**
     * 根据站台列表搜索车次
     */
    List<TrainRoutePairDO> searchTrainsByStationList(
            @Param("start") List<StationDO> departureStations,
            @Param("end") List<StationDO> arrivalStations
    );
    /**
     * 批量插入线路对
     * @param list 数据列表
     * @return 影响行数
     */
    int batchInsert(@Param("list") List<TrainRoutePairDO> list);
}
