package com.lalal.modules.sse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 事件数据传输对象
 * 统一封装所有 SSE 事件类型，替代手动拼 JSON
 *
 * 事件格式规范（供前端 Step 8 对接）：
 * ┌─────────────┬──────────────────────┬──────────────────────────────────────────────┐
 * │ 事件名(event)│ 触发时机              │ 数据结构                                      │
 * ├─────────────┼──────────────────────┼──────────────────────────────────────────────┤
 * │ start       │ 流开始                │ {type:"start",conversationId,model}           │
 * │ message     │ 每个文本 token        │ {type:"text",content,conversationId}          │
 * │ tool_call   │ AI 决定调用工具       │ {type:"tool_call",toolName,arguments,convId}  │
 * │ tool_result │ 工具执行完成          │ {type:"tool_result",toolName,result,convId}   │
 * │ thinking    │ AI 正在推理/执行工具  │ {type:"thinking",content,conversationId}      │
 * │ confirm     │ 需要人工确认          │ {type:"confirm",content,confirmId,convId}     │
 * │ error       │ 出错                  │ {type:"error",content,conversationId}         │
 * │ done        │ 流结束                │ {type:"done",conversationId}                  │
 * └─────────────┴──────────────────────┴──────────────────────────────────────────────┘
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEventDto {

    /** 事件类型：start/text/tool_call/tool_result/thinking/confirm/error/done */
    private String type;

    /** 文本内容（type=text/thinking/error/confirm 时使用） */
    private String content;

    /** 会话ID */
    private String conversationId;

    /** 工具名称（type=tool_call/tool_result 时使用） */
    private String toolName;

    /** 工具参数 JSON（type=tool_call 时使用） */
    private String arguments;

    /** 工具执行结果摘要（type=tool_result 时使用） */
    private String result;

    /** 确认操作ID（type=confirm 时使用） */
    private String confirmId;

    /** 模型名称（type=start 时使用） */
    private String model;

    // ========== 静态工厂方法 ==========

    /** 流开始事件 */
    public static SseEventDto start(String conversationId, String model) {
        return SseEventDto.builder()
                .type("start")
                .conversationId(conversationId)
                .model(model)
                .build();
    }

    /** 文本 token 事件 */
    public static SseEventDto text(String content, String conversationId) {
        return SseEventDto.builder()
                .type("text")
                .content(content)
                .conversationId(conversationId)
                .build();
    }

    /** 工具调用事件 */
    public static SseEventDto toolCall(String toolName, String arguments, String conversationId) {
        return SseEventDto.builder()
                .type("tool_call")
                .toolName(toolName)
                .arguments(arguments)
                .conversationId(conversationId)
                .build();
    }

    /** 工具执行结果事件 */
    public static SseEventDto toolResult(String toolName, String result, String conversationId) {
        return SseEventDto.builder()
                .type("tool_result")
                .toolName(toolName)
                .result(result)
                .conversationId(conversationId)
                .build();
    }

    /** 思考/进度事件 */
    public static SseEventDto thinking(String content, String conversationId) {
        return SseEventDto.builder()
                .type("thinking")
                .content(content)
                .conversationId(conversationId)
                .build();
    }

    /** 人工确认事件 */
    public static SseEventDto confirm(String content, String confirmId, String conversationId) {
        return SseEventDto.builder()
                .type("confirm")
                .content(content)
                .confirmId(confirmId)
                .conversationId(conversationId)
                .build();
    }

    /** 错误事件 */
    public static SseEventDto error(String content, String conversationId) {
        return SseEventDto.builder()
                .type("error")
                .content(content)
                .conversationId(conversationId)
                .build();
    }

    /** 流结束事件 */
    public static SseEventDto done(String conversationId) {
        return SseEventDto.builder()
                .type("done")
                .conversationId(conversationId)
                .build();
    }
}
