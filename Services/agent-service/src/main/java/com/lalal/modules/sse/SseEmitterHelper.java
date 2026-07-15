package com.lalal.modules.sse;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SseEmitter 工具类
 * 封装 SSE 事件发送操作，统一使用 fastjson2 序列化
 *
 * 职责：
 * 1. 创建带完整生命周期管理的 SseEmitter
 * 2. 统一 JSON 序列化（替代手动拼 JSON）
 * 3. 安全发送（捕获 IOException，自动 completeWithError）
 * 4. 心跳保活（防止连接超时断开）
 */
@Slf4j
@Component
public class SseEmitterHelper {

    /** 心跳调度器（全局共享，daemon线程不阻止JVM退出） */
    private static final ScheduledExecutorService KEEP_ALIVE_SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "sse-keepalive");
                t.setDaemon(true);
                return t;
            });

    /**
     * 创建带完整生命周期管理的 SseEmitter
     *
     * @param timeoutMs 超时时间（毫秒）
     * @return SseEmitter
     */
    public SseEmitter createEmitter(long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout");
            emitter.complete();
        });

        emitter.onCompletion(() -> log.debug("SSE connection completed"));

        emitter.onError(ex -> {
            log.error("SSE connection error: {}", ex.getMessage());
            emitter.completeWithError(ex);
        });

        return emitter;
    }

    /**
     * 创建带心跳保活的 SseEmitter
     *
     * @param timeoutMs 超时时间
     * @param keepAliveIntervalMs 心跳间隔（毫秒）
     * @return SseEmitter + 心跳 Future（用于取消心跳）
     */
    public SseEmitterWithKeepAlive createEmitterWithKeepAlive(long timeoutMs, long keepAliveIntervalMs) {
        SseEmitter emitter = createEmitter(timeoutMs);

        // 启动心跳
        ScheduledFuture<?> keepAliveFuture = KEEP_ALIVE_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            } catch (IOException e) {
                log.debug("SSE heartbeat failed, connection likely closed");
                // 心跳失败说明连接已断开，取消心跳
                Thread.currentThread().interrupt();
            }
        }, keepAliveIntervalMs, keepAliveIntervalMs, TimeUnit.MILLISECONDS);

        // 连接关闭时取消心跳
        emitter.onCompletion(() -> keepAliveFuture.cancel(false));
        emitter.onTimeout(() -> keepAliveFuture.cancel(false));
        emitter.onError(ex -> keepAliveFuture.cancel(false));

        return new SseEmitterWithKeepAlive(emitter, keepAliveFuture);
    }

    /**
     * 发送 SSE 事件（自动 fastjson2 序列化）
     *
     * @param emitter SseEmitter
     * @param eventName 事件名（start/message/tool_call/tool_result/thinking/confirm/error/done）
     * @param data 事件数据
     */
    public void sendEvent(SseEmitter emitter, String eventName, SseEventDto data) {
        safeSend(emitter, eventName, data);
    }

    /**
     * 发送文本 token 事件
     */
    public void sendToken(SseEmitter emitter, String token, String conversationId) {
        sendEvent(emitter, "message", SseEventDto.text(token, conversationId));
    }

    /**
     * 发送工具调用事件
     */
    public void sendToolCall(SseEmitter emitter, String toolName, String arguments, String conversationId) {
        sendEvent(emitter, "tool_call", SseEventDto.toolCall(toolName, arguments, conversationId));
    }

    /**
     * 发送工具执行结果事件
     */
    public void sendToolResult(SseEmitter emitter, String toolName, String result, String conversationId) {
        sendEvent(emitter, "tool_result", SseEventDto.toolResult(toolName, result, conversationId));
    }

    /**
     * 发送思考/进度事件
     */
    public void sendThinking(SseEmitter emitter, String content, String conversationId) {
        sendEvent(emitter, "thinking", SseEventDto.thinking(content, conversationId));
    }

    /**
     * 发送人工确认事件
     */
    public void sendConfirm(SseEmitter emitter, String content, String confirmId, String conversationId) {
        sendEvent(emitter, "confirm", SseEventDto.confirm(content, confirmId, conversationId));
    }

    /**
     * 发送流开始事件
     */
    public void sendStart(SseEmitter emitter, String conversationId, String model) {
        sendEvent(emitter, "start", SseEventDto.start(conversationId, model));
    }

    /**
     * 发送流结束事件
     */
    public void sendDone(SseEmitter emitter, String conversationId) {
        sendEvent(emitter, "done", SseEventDto.done(conversationId));
    }

    /**
     * 发送错误事件
     */
    public void sendError(SseEmitter emitter, String content, String conversationId) {
        sendEvent(emitter, "error", SseEventDto.error(content, conversationId));
    }

    /**
     * 安全发送 SSE 事件
     * 捕获 IOException，自动 completeWithError
     *
     * @param emitter SseEmitter
     * @param eventName 事件名
     * @param data 数据对象（自动 fastjson2 序列化）
     */
    public void safeSend(SseEmitter emitter, String eventName, Object data) {
        try {
            String jsonData = JSON.toJSONString(data, JSONWriter.Feature.IgnoreNoneValue);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData));
        } catch (IOException e) {
            log.error("SSE send failed for event '{}': {}", eventName, e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // emitter 可能已经关闭
            }
        }
    }

    /**
     * 安全完成 SseEmitter
     */
    public void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // emitter 可能已经关闭
        }
    }

    /**
     * 安全完成 SseEmitter（带错误）
     */
    public void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (Exception ignored) {
            // emitter 可能已经关闭
        }
    }

    /**
     * 带心跳保活的 SseEmitter 包装
     */
    public record SseEmitterWithKeepAlive(SseEmitter emitter, ScheduledFuture<?> keepAliveFuture) {
        /**
         * 取消心跳
         */
        public void cancelKeepAlive() {
            if (keepAliveFuture != null && !keepAliveFuture.isCancelled()) {
                keepAliveFuture.cancel(false);
            }
        }
    }
}
