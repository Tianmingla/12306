package com.lalal.modules.strategy;

import com.lalal.modules.dao.CarriageDO;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;


import java.util.List;

/**
 * 选座策略接口
 */
public interface SeatSelectionStrategy {
    /**
     * 选择座位
     * @param request 选座请求
     * @param carriages 可选车厢列表
     * @return 选中的车票信息，如果选座失败返回null
     */
    TicketDTO select(SeatSelectionRequestDTO request, List<CarriageDO> carriages);
    
    /**
     * 策略支持的座位类型
     */
    int getSeatType();
}
