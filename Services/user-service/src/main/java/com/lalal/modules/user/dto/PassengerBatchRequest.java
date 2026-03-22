package com.lalal.modules.user.dto;

import lombok.Data;

import java.util.List;

/**
 * ticket-service 等服务间调用：按用户与乘车人 id 批量查询
 */
@Data
public class PassengerBatchRequest {
    private Long userId;
    private List<Long> passengerIds;
}
