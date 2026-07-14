package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.AgentMemoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话记忆 Mapper
 */
@Mapper
public interface AgentMemoryMapper extends BaseMapper<AgentMemoryDO> {
}
