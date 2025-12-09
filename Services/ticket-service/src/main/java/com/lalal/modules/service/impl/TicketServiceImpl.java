package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.service.TicketService;
import org.springframework.stereotype.Service;

@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService {
}
