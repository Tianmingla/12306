/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalal.modules.service;

import com.lalal.modules.dto.request.WaitlistCreateRequestDTO;
import com.lalal.modules.dto.response.WaitlistOrderVO;
import com.lalal.modules.entity.WaitlistOrderDO;

import java.util.List;

/**
 * 候补购票服务接口
 */
public interface WaitlistService {

    /**
     * 创建候补订单
     *
     * @param request 候补请求
     * @return 候补订单号
     */
    String createWaitlist(WaitlistCreateRequestDTO request);

    /**
     * 取消候补订单
     *
     * @param waitlistSn 候补订单号
     * @param username 用户账号
     */
    void cancelWaitlist(String waitlistSn, String username);

    /**
     * 获取用户候补订单列表
     *
     * @param username 用户账号
     * @return 候补订单列表
     */
    List<WaitlistOrderVO> getWaitlistOrders(String username);

    /**
     * 获取候补订单详情
     *
     * @param waitlistSn 候补订单号
     * @param username 用户账号
     * @return 候补订单详情
     */
    WaitlistOrderVO getWaitlistDetail(String waitlistSn, String username);

    /**
     * 检查并兑现候补订单（定时任务调用）
     */
    void checkAndFulfillWaitlistOrders();

    /**
     * 根据候补订单号查询
     */
    WaitlistOrderDO findByWaitlistSn(String waitlistSn);

    /**
     * 更新候补订单状态
     */
    void updateWaitlistStatus(String waitlistSn, Integer status);

    /**
     * 更新候补订单状态和订单号
     */
    void updateWaitlistStatus(String waitlistSn, Integer status, String orderSn);

    /**
     * 重新计算优先级
     */
    void recalculatePriority(WaitlistOrderDO order);

    /**
     * 失败惩罚：降低优先级
     */
    void recalculatePriorityWithPenalty(WaitlistOrderDO order);
}
