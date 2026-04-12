package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.WaitlistLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 候补订单操作日志 Mapper
 */
@Mapper
public interface WaitlistLogMapper extends BaseMapper<WaitlistLogDO> {

    /**
     * 批量插入操作日志
     */
    int insertBatch(List<WaitlistLogDO> logList);
}
