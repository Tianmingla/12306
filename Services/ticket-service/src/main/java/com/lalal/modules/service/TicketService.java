package com.lalal.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketDO;
import com.lalal.modules.result.Result;

public interface TicketService extends IService<TicketDO> {
    PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto);
    PurchaseTicketVO check(String RequestId);

}
