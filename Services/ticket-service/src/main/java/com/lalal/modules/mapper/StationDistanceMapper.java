package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.StationDistanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StationDistanceMapper extends BaseMapper<StationDistanceDO> {

    /**
     * 根据列车ID和站点名称查询站间距离
     *
     * @param trainId 列车ID
     * @param departureStationName 出发站名称
     * @param arrivalStationName 到达站名称
     * @return 站间距离记录
     */
    StationDistanceDO selectByTrainAndStations(@Param("trainId") Long trainId,
                                                @Param("departureStationName") String departureStationName,
                                                @Param("arrivalStationName") String arrivalStationName);

    /**
     * 根据车次号和站点名称查询站间距离
     *
     * @param trainNumber 车次号
     * @param departureStationName 出发站名称
     * @param arrivalStationName 到达站名称
     * @return 站间距离记录
     */
    StationDistanceDO selectByTrainNumberAndStations(@Param("trainNumber") String trainNumber,
                                                      @Param("departureStationName") String departureStationName,
                                                      @Param("arrivalStationName") String arrivalStationName);
}
