package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.TrainFareConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrainFareConfigMapper extends BaseMapper<TrainFareConfigDO> {

    /**
     * 根据列车ID查询票价配置
     *
     * @param trainId 列车ID
     * @return 票价配置
     */
    TrainFareConfigDO selectByTrainId(@Param("trainId") Long trainId);

    /**
     * 根据车次号查询票价配置
     *
     * @param trainNumber 车次号
     * @return 票价配置
     */
    TrainFareConfigDO selectByTrainNumber(@Param("trainNumber") String trainNumber);
}
