package com.lalal.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketAsyncRequestMapper extends BaseMapper<TicketAsyncRequestDO> {

    /**
     * 根据 requestId 查询记录
     */
    @Select("SELECT * FROM t_ticket_async_request WHERE request_id = #{requestId} AND del_flag = 0")
    TicketAsyncRequestDO selectByRequestId(@Param("requestId") String requestId);
}
