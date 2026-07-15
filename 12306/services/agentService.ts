/**
 * Agent 智能客服 SSE 服务
 * 对接后端 /api/agent/chat/stream 接口
 *
 * SSE 事件格式：
 * - start:      流开始 {type:"start", conversationId, model}
 * - message:    文本 token {type:"text", content, conversationId}
 * - tool_call:  工具调用 {type:"tool_call", toolName, arguments, conversationId}
 * - tool_result:工具结果 {type:"tool_result", toolName, result, conversationId}
 * - thinking:   思考进度 {type:"thinking", content, conversationId}
 * - confirm:    需确认   {type:"confirm", content, confirmId, conversationId}
 * - error:      错误     {type:"error", content, conversationId}
 * - done:       流结束   {type:"done", conversationId}
 * - heartbeat:  心跳保活 {type:"heartbeat", timestamp}
 */

import { API_BASE, authHeaders } from './http';

/** SSE 事件回调 */
export interface AgentStreamCallbacks {
  /** 流开始 */
  onStart?: (conversationId: string, model: string) => void;
  /** 收到文本 token */
  onToken?: (content: string, conversationId: string) => void;
  /** 工具调用开始 */
  onToolCall?: (toolName: string, arguments: string, conversationId: string) => void;
  /** 工具执行结果 */
  onToolResult?: (toolName: string, result: string, conversationId: string) => void;
  /** 思考/进度提示 */
  onThinking?: (content: string, conversationId: string) => void;
  /** 需要人工确认 */
  onConfirm?: (content: string, confirmId: string, conversationId: string) => void;
  /** 错误 */
  onError?: (content: string, conversationId: string) => void;
  /** 流结束 */
  onDone?: (conversationId: string) => void;
}

/**
 * 发送消息到 Agent 并接收 SSE 流式响应
 *
 * @param message 用户消息
 * @param conversationId 会话ID（可选，首次为空）
 * @param callbacks SSE 事件回调
 * @returns AbortController（可用于取消请求）
 */
export function sendMessageToAgent(
  message: string,
  conversationId: string | null,
  callbacks: AgentStreamCallbacks
): AbortController {
  const controller = new AbortController();

  const url = `${API_BASE}/agent/chat/stream`;

  fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({
      message,
      conversationId: conversationId || undefined,
      stream: true,
    }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        const text = await response.text();
        callbacks.onError?.(`请求失败: ${response.status} ${text}`, conversationId || '');
        callbacks.onDone?.(conversationId || '');
        return;
      }

      if (!response.body) {
        callbacks.onError?.('浏览器不支持流式响应', conversationId || '');
        callbacks.onDone?.(conversationId || '');
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // 按双换行分割 SSE 事件
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // 最后一行可能不完整，保留

        let currentEvent = '';
        let currentData = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            currentData = line.slice(5).trim();
          } else if (line === '' && currentEvent && currentData) {
            // 空行 = 事件结束，触发回调
            handleSseEvent(currentEvent, currentData, callbacks);
            currentEvent = '';
            currentData = '';
          }
        }
      }

      // 处理 buffer 中剩余的数据
      if (buffer.trim()) {
        const remaining = buffer.trim();
        if (remaining.startsWith('data:')) {
          const data = remaining.slice(5).trim();
          // 尝试找到对应的 event
          handleSseEvent('', data, callbacks);
        }
      }
    })
    .catch((err) => {
      if (err.name === 'AbortError') return; // 用户主动取消
      console.error('Agent SSE error:', err);
      callbacks.onError?.('连接失败，请检查网络', conversationId || '');
      callbacks.onDone?.(conversationId || '');
    });

  return controller;
}

/**
 * 发送确认操作到后端
 */
export async function confirmAction(
  confirmId: string,
  approved: boolean
): Promise<{ success: boolean; content: string }> {
  try {
    const url = `${API_BASE}/agent/confirm/${confirmId}?approved=${approved}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: authHeaders(),
    });
    const result = await response.json();
    if (result.code === 200) {
      return { success: true, content: result.data?.content || (approved ? '操作已确认' : '操作已取消') };
    }
    return { success: false, content: result.message || '操作失败' };
  } catch (err) {
    return { success: false, content: '网络错误，请重试' };
  }
}

/**
 * 解析并分发 SSE 事件
 */
function handleSseEvent(event: string, data: string, callbacks: AgentStreamCallbacks) {
  try {
    const parsed = JSON.parse(data);

    switch (event) {
      case 'start':
        callbacks.onStart?.(parsed.conversationId, parsed.model);
        break;
      case 'message':
        if (parsed.type === 'text' && parsed.content) {
          callbacks.onToken?.(parsed.content, parsed.conversationId);
        }
        break;
      case 'tool_call':
        callbacks.onToolCall?.(parsed.toolName, parsed.arguments, parsed.conversationId);
        break;
      case 'tool_result':
        callbacks.onToolResult?.(parsed.toolName, parsed.result, parsed.conversationId);
        break;
      case 'thinking':
        callbacks.onThinking?.(parsed.content, parsed.conversationId);
        break;
      case 'confirm':
        callbacks.onConfirm?.(parsed.content, parsed.confirmId, parsed.conversationId);
        break;
      case 'error':
        callbacks.onError?.(parsed.content, parsed.conversationId);
        break;
      case 'done':
        callbacks.onDone?.(parsed.conversationId);
        break;
      case 'heartbeat':
        // 心跳，忽略
        break;
      default:
        // 未知事件，尝试用 type 字段分发
        if (parsed.type) {
          switch (parsed.type) {
            case 'text':
              callbacks.onToken?.(parsed.content, parsed.conversationId);
              break;
            case 'start':
              callbacks.onStart?.(parsed.conversationId, parsed.model);
              break;
            case 'done':
              callbacks.onDone?.(parsed.conversationId);
              break;
          }
        }
    }
  } catch (e) {
    console.warn('Failed to parse SSE event:', event, data, e);
  }
}
