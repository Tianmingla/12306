package com.lalal.modules.service;

import com.lalal.modules.dto.AsyncTicketPurchaseMessage;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;

/**
 * 异步购票服务接口
 */
public interface AsyncTicketPurchaseService {

    /**
     * 创建异步购票请求
     * @param message 购票消息
     * @return 请求ID
     */
    String createAsyncRequest(AsyncTicketPurchaseMessage message);

    /**
     * 处理异步购票消息
     * @param message 购票消息
     */
    void processAsyncPurchase(AsyncTicketPurchaseMessage message);

    /**
     * 查询异步购票状态
     * @param requestId 请求ID
     * @param userId 用户ID
     * @return 查询结果
     */
    AsyncTicketCheckVO checkStatus(String requestId, Long userId);
}
