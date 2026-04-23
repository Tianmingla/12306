package com.lalal.modules.controller;

import com.lalal.modules.dto.ReminderState;
import com.lalal.modules.service.ReminderService;
import com.lalal.modules.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 出行提醒控制器
 *
 * 提供晚点/停运状态更新接口
 * 模拟真实场景中调度系统的状态变更
 */
@RestController
@RequestMapping("/api/reminder")
@RequiredArgsConstructor
@Slf4j
public class ReminderController {

    private final ReminderService reminderService;

    @Value("${reminder.enabled:true}")
    private boolean reminderEnabled;

    /**
     * 更新列车晚点状态
     *
     * 模拟调度系统检测到晚点后调用
     * 更新缓存状态，后续提醒消息会自动重新调度
     *
     * @param trainNumber 车次号
     * @param date 乘车日期 (yyyy-MM-dd)
     * @param delayMinutes 晚点分钟数
     */
    @PostMapping("/delay")
    public Result<String> updateDelay(
            @RequestParam String trainNumber,
            @RequestParam String date,
            @RequestParam int delayMinutes) {

        if (!reminderEnabled) {
            return Result.fail("提醒功能已禁用");
        }

        log.info("[晚点更新] trainNumber={}, date={}, delayMinutes={}", trainNumber, date, delayMinutes);

        try {
            reminderService.updateTrainDelay(trainNumber, date, delayMinutes);
            return Result.success("晚点状态已更新");
        } catch (Exception e) {
            log.error("[晚点更新] 失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新列车停运状态
     *
     * @param trainNumber 车次号
     * @param date 乘车日期 (yyyy-MM-dd)
     */
    @PostMapping("/cancel")
    public Result<String> updateCancel(
            @RequestParam String trainNumber,
            @RequestParam String date) {

        if (!reminderEnabled) {
            return Result.fail("提醒功能已禁用");
        }

        log.info("[停运更新] trainNumber={}, date={}", trainNumber, date);

        try {
            reminderService.updateTrainCancel(trainNumber, date);
            return Result.success("停运状态已更新");
        } catch (Exception e) {
            log.error("[停运更新] 失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 查询订单提醒状态
     *
     * @param orderSn 订单号
     */
    @GetMapping("/state/{orderSn}")
    public Result<ReminderState> getReminderState(@PathVariable String orderSn) {
        ReminderState state = reminderService.getReminderState(orderSn);
        if (state == null) {
            return Result.fail("提醒状态不存在或已过期");
        }
        return Result.success(state);
    }

    /**
     * 查看提醒功能状态
     */
    @GetMapping("/status")
    public Result<Boolean> getStatus() {
        return Result.success(reminderEnabled);
    }
}