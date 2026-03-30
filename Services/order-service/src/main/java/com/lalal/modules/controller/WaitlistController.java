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

package com.lalal.modules.controller;

import com.lalal.modules.dto.request.WaitlistCreateRequestDTO;
import com.lalal.modules.dto.response.WaitlistOrderVO;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 候补购票控制器
 *
 * 提供候补购票相关 API：
 * - 创建候补订单
 * - 取消候补
 * - 查询候补列表
 * - 查询候补详情
 */
@RestController
@RequestMapping("/api/order/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    /**
     * 创建候补订单
     */
    @PostMapping("/create")
    public Result<String> createWaitlist(
            @RequestBody WaitlistCreateRequestDTO request,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {

        if (phone == null || phone.isBlank()) {
            return Result.fail("请先登录", ReturnCode.fail.code());
        }

        request.setAccount(phone);

        try {
            String waitlistSn = waitlistService.createWaitlist(request);
            return Result.success(waitlistSn);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 取消候补订单
     */
    @PostMapping("/cancel/{waitlistSn}")
    public Result<Void> cancelWaitlist(
            @PathVariable String waitlistSn,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {

        if (phone == null || phone.isBlank()) {
            return Result.fail("请先登录", ReturnCode.fail.code());
        }

        try {
            waitlistService.cancelWaitlist(waitlistSn, phone);
            return Result.success(null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 获取用户候补订单列表
     */
    @GetMapping("/list")
    public Result<List<WaitlistOrderVO>> getWaitlistOrders(
            @RequestHeader(value = "X-User-Name", required = false) String phone) {

        if (phone == null || phone.isBlank()) {
            return Result.success(List.of());
        }

        List<WaitlistOrderVO> orders = waitlistService.getWaitlistOrders(phone);
        return Result.success(orders);
    }

    /**
     * 获取候补订单详情
     */
    @GetMapping("/detail/{waitlistSn}")
    public Result<WaitlistOrderVO> getWaitlistDetail(
            @PathVariable String waitlistSn,
            @RequestHeader(value = "X-User-Name", required = false) String phone) {

        if (phone == null || phone.isBlank()) {
            return Result.fail("请先登录", ReturnCode.fail.code());
        }

        try {
            WaitlistOrderVO detail = waitlistService.getWaitlistDetail(waitlistSn, phone);
            return Result.success(detail);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }
}
