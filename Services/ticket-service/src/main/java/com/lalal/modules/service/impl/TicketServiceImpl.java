package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.service.TicketService;
import org.springframework.stereotype.Service;

@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService {
    @Override
    public PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto) {
        //TODO 过滤器 请求参数验证
        PurchaseTicketVO purchaseTicketVO=new PurchaseTicketVO();
        //TODO 检查当前流量状态 目前默认false
        boolean peakHour=false;
        if(peakHour){
            //TODO
            //1.发送消息到座位服务 消息内容就是该函数参数 消息id==请求id(RequestContext.getRequestId())
            purchaseTicketVO.setStatus(RequestStatus.PROCESSING.toString());
            return purchaseTicketVO;
        }else{
            //调用座位服务

            //调用订单服务

            purchaseTicketVO.setStatus(RequestStatus.SUCCESS.toString());
            return purchaseTicketVO;
        }
    }

    @Override
    public PurchaseTicketVO check(String RequestId) {
        return null;
    }
}
