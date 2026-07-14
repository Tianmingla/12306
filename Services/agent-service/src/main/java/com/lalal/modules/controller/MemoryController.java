package com.lalal.modules.controller;

import com.lalal.modules.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

/**
 * 记忆管理控制器
 * 提供对话记忆的查询和清理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final ChatMemory chatMemory;

    /**
     * 清除指定会话的记忆
     */
    @DeleteMapping("/{conversationId}")
    public Result<String> clearMemory(@PathVariable String conversationId,
                                       @RequestHeader("X-User-Id") String userId) {
        log.info("Clear memory: conversationId={}, userId={}", conversationId, userId);
        chatMemory.clear(conversationId);
        return Result.success("对话记忆已清除");
    }

    /**
     * 获取会话的消息数量
     */
    @GetMapping("/{conversationId}/count")
    public Result<Integer> getMessageCount(@PathVariable String conversationId,
                                            @RequestHeader("X-User-Id") String userId) {
        int count = chatMemory.get(conversationId).size();
        return Result.success(count);
    }
}
