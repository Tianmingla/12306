package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.TicketDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<TicketDO> {
}
