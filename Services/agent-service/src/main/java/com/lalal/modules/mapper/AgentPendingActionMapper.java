package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.AgentPendingActionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待确认操作 Mapper
 */
@Mapper
public interface AgentPendingActionMapper extends BaseMapper<AgentPendingActionDO> {
}
