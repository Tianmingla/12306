package com.lalal.modules.controller;

import com.lalal.modules.dto.ChatRequest;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI Agent 智能客服控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * SSE 流式对话接口
     * 实时输出智能体思考和执行过程
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request,
                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("SSE chat request from user: {}, conversation: {}", userId, request.getConversationId());
        return agentService.streamChat(request, userId);
    }

    /**
     * 同步对话接口（简单问答场景）
     */
    @PostMapping("/chat")
    public Result<com.lalal.modules.dto.ChatResponse> chat(@RequestBody ChatRequest request,
                                                            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("Sync chat request from user: {}, conversation: {}", userId, request.getConversationId());
        return Result.success(agentService.chat(request, userId));
    }

    /**
     * 人工确认操作
     * Human-in-the-Loop：用户确认后执行关键操作
     */
    @PostMapping("/confirm/{confirmId}")
    public Result<com.lalal.modules.dto.ChatResponse> confirmAction(
            @PathVariable String confirmId,
            @RequestParam boolean approved,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Confirm action: confirmId={}, approved={}, userId={}", confirmId, approved, userId);
        return Result.success(agentService.confirmAction(confirmId, approved, userId));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Agent service is running");
    }
}
