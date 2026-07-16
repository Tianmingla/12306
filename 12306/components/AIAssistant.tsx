
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { MessageSquare, X, Send, Sparkles, Loader2, Wrench, Brain, CheckCircle, XCircle, AlertCircle, Copy, Check } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github.css';
import { sendMessageToAgent, confirmAction } from '../services/agentService';
import { ChatMessage } from '../types';

/** 工具名 → 中文标签映射 */
const TOOL_LABELS: Record<string, string> = {
  searchDirectTrains: '搜索车次',
  searchTransferTrains: '搜索换乘',
  getTrainStationDetails: '查询经停站',
  queryOrderDetail: '查询订单',
  queryMyOrders: '查询订单列表',
  refundTicket: '退票',
  cancelOrder: '取消订单',
  queryWaitlistOrders: '查询候补',
  queryMyPassengers: '查询乘车人',
};

/** 代码块组件 — 带语言标签 + 复制按钮 */
const CodeBlock: React.FC<{ className?: string; children: React.ReactNode }> = ({ className, children }) => {
  const [copied, setCopied] = useState(false);
  const match = /language-(\w+)/.exec(className || '');
  const language = match ? match[1] : '';
  const codeText = String(children).replace(/\n$/, '');

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(codeText);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* ignore */ }
  };

  return (
    <div className="relative group my-2 rounded-lg overflow-hidden border border-gray-200 bg-gray-50">
      {/* 顶栏：语言标签 + 复制按钮 */}
      <div className="flex items-center justify-between px-3 py-1.5 bg-gray-100 border-b border-gray-200 text-xs text-gray-500">
        <span>{language || 'code'}</span>
        <button
          onClick={handleCopy}
          className="flex items-center space-x-1 hover:text-gray-700 transition-colors"
        >
          {copied ? <Check className="h-3 w-3 text-green-500" /> : <Copy className="h-3 w-3" />}
          <span>{copied ? '已复制' : '复制'}</span>
        </button>
      </div>
      {/* 代码内容 — rehype-highlight 已处理高亮 */}
      <pre className="!m-0 !p-3 overflow-x-auto text-xs leading-relaxed">
        <code className={className}>{children}</code>
      </pre>
    </div>
  );
};

const AIAssistant: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      role: 'model',
      text: '您好！我是智行客服助手 🚄 请问有什么可以帮您？查询车票、了解退改签政策还是规划行程？',
      timestamp: new Date(),
      type: 'text',
    }
  ]);
  const [isLoading, setIsLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const streamingMsgIdRef = useRef<string | null>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isOpen]);

  /** 获取或创建流式消息的 ID */
  const getOrCreateStreamingMsgId = useCallback(() => {
    if (streamingMsgIdRef.current) return streamingMsgIdRef.current;
    const id = Date.now().toString();
    streamingMsgIdRef.current = id;
    return id;
  }, []);

  /** 追加 token 到正在流式输出的消息 */
  const appendToken = useCallback((token: string, convId: string) => {
    const msgId = getOrCreateStreamingMsgId();
    setMessages(prev => {
      const existing = prev.find(m => m.id === msgId);
      if (existing) {
        return prev.map(m =>
          m.id === msgId ? { ...m, text: m.text + token } : m
        );
      }
      // 创建新的流式消息
      return [...prev, {
        id: msgId,
        role: 'model' as const,
        text: token,
        timestamp: new Date(),
        type: 'text' as const,
        conversationId: convId,
        isStreaming: true,
      }];
    });
  }, [getOrCreateStreamingMsgId]);

  const handleSend = async () => {
    if (!input.trim() || isLoading) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      text: input,
      timestamp: new Date(),
      type: 'text',
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsLoading(true);
    streamingMsgIdRef.current = null;

    const controller = sendMessageToAgent(input, conversationId, {
      onStart: (convId, model) => {
        setConversationId(convId);
      },
      onToken: (content, convId) => {
        appendToken(content, convId);
      },
      onToolCall: (toolName, _args, convId) => {
        const label = TOOL_LABELS[toolName] || toolName;
        const msg: ChatMessage = {
          id: (Date.now() + 1).toString(),
          role: 'model',
          text: `🔧 ${label}...`,
          timestamp: new Date(),
          type: 'tool_call',
          toolName,
          conversationId: convId,
        };
        setMessages(prev => [...prev, msg]);
        streamingMsgIdRef.current = null; // 下一个 token 创建新消息
      },
      onToolResult: (toolName, result, convId) => {
        const label = TOOL_LABELS[toolName] || toolName;
        const msg: ChatMessage = {
          id: (Date.now() + 2).toString(),
          role: 'model',
          text: `✅ ${label}完成`,
          timestamp: new Date(),
          type: 'tool_result',
          toolName,
          conversationId: convId,
        };
        setMessages(prev => [...prev, msg]);
        streamingMsgIdRef.current = null;
      },
      onThinking: (content, _convId) => {
        const msg: ChatMessage = {
          id: (Date.now() + 3).toString(),
          role: 'model',
          text: content,
          timestamp: new Date(),
          type: 'thinking',
        };
        setMessages(prev => [...prev, msg]);
        streamingMsgIdRef.current = null;
      },
      onConfirm: (content, confirmId, convId) => {
        const msg: ChatMessage = {
          id: (Date.now() + 4).toString(),
          role: 'model',
          text: content,
          timestamp: new Date(),
          type: 'confirm',
          needConfirm: true,
          confirmId,
          conversationId: convId,
        };
        setMessages(prev => [...prev, msg]);
        streamingMsgIdRef.current = null;
      },
      onError: (content, _convId) => {
        const msg: ChatMessage = {
          id: (Date.now() + 5).toString(),
          role: 'model',
          text: `⚠️ ${content}`,
          timestamp: new Date(),
          type: 'text',
        };
        setMessages(prev => [...prev, msg]);
        streamingMsgIdRef.current = null;
      },
      onDone: (_convId) => {
        // 标记所有流式消息完成（不依赖 ref，因为中间事件可能已清空 ref）
        setMessages(prev =>
          prev.map(m =>
            m.isStreaming ? { ...m, isStreaming: false } : m
          )
        );
        setIsLoading(false);
        streamingMsgIdRef.current = null;
      },
    });

    abortRef.current = controller;
  };

  const handleConfirm = async (confirmId: string, approved: boolean) => {
    // 更新消息状态
    setMessages(prev =>
      prev.map(m =>
        m.confirmId === confirmId
          ? { ...m, needConfirm: false, text: approved ? '✅ 已确认，正在执行...' : '❌ 已取消操作' }
          : m
      )
    );

    const result = await confirmAction(confirmId, approved);
    if (!result.success) {
      setMessages(prev =>
        prev.map(m =>
          m.confirmId === confirmId ? { ...m, text: `⚠️ ${result.content}` } : m
        )
      );
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  /** 渲染单条消息 */
  const renderMessage = (msg: ChatMessage) => {
    // 确认消息
    if (msg.type === 'confirm' && msg.needConfirm) {
      return (
        <div className="bg-amber-50 border border-amber-200 rounded-2xl rounded-tl-none px-4 py-3 shadow-sm max-w-[85%]">
          <div className="flex items-start space-x-2 mb-2">
            <AlertCircle className="h-4 w-4 text-amber-500 mt-0.5 flex-shrink-0" />
            <span className="text-sm text-amber-800">{msg.text}</span>
          </div>
          <div className="flex space-x-2">
            <button
              onClick={() => handleConfirm(msg.confirmId!, true)}
              className="flex-1 px-3 py-1.5 bg-green-500 text-white text-xs rounded-lg hover:bg-green-600 transition-colors flex items-center justify-center space-x-1"
            >
              <CheckCircle className="h-3 w-3" />
              <span>确认</span>
            </button>
            <button
              onClick={() => handleConfirm(msg.confirmId!, false)}
              className="flex-1 px-3 py-1.5 bg-red-500 text-white text-xs rounded-lg hover:bg-red-600 transition-colors flex items-center justify-center space-x-1"
            >
              <XCircle className="h-3 w-3" />
              <span>取消</span>
            </button>
          </div>
        </div>
      );
    }

    // 工具调用消息
    if (msg.type === 'tool_call') {
      return (
        <div className="bg-blue-50 border border-blue-100 rounded-2xl rounded-tl-none px-4 py-2 shadow-sm max-w-[80%]">
          <div className="flex items-center space-x-2">
            <Wrench className="h-3.5 w-3.5 text-blue-500 animate-pulse" />
            <span className="text-xs text-blue-600 font-medium">{msg.text}</span>
          </div>
        </div>
      );
    }

    // 工具结果消息
    if (msg.type === 'tool_result') {
      return (
        <div className="bg-green-50 border border-green-100 rounded-2xl rounded-tl-none px-4 py-2 shadow-sm max-w-[80%]">
          <div className="flex items-center space-x-2">
            <CheckCircle className="h-3.5 w-3.5 text-green-500" />
            <span className="text-xs text-green-600">{msg.text}</span>
          </div>
        </div>
      );
    }

    // 思考消息
    if (msg.type === 'thinking') {
      return (
        <div className="bg-purple-50 border border-purple-100 rounded-2xl rounded-tl-none px-4 py-2 shadow-sm max-w-[80%]">
          <div className="flex items-center space-x-2">
            <Brain className="h-3.5 w-3.5 text-purple-400" />
            <span className="text-xs text-purple-500 italic">{msg.text}</span>
          </div>
        </div>
      );
    }

    // 用户消息 — 纯文本
    if (msg.role === 'user') {
      return (
        <div className="max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm bg-blue-600 text-white rounded-tr-none whitespace-pre-wrap">
          {msg.text}
        </div>
      );
    }

    // 模型文本消息 — Markdown 渲染
    return (
      <div className="max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm bg-white text-gray-800 border border-gray-100 rounded-tl-none">
        <div className="markdown-body">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            rehypePlugins={[rehypeHighlight]}
            components={{
              // 代码块：有语言标记的用 CodeBlock 组件，行内代码用 <code>
              code({ className, children, ...props }) {
                const isBlock = /language-/.test(className || '');
                if (isBlock) {
                  return <CodeBlock className={className}>{children}</CodeBlock>;
                }
                // 行内代码
                return (
                  <code className="bg-gray-100 text-pink-600 px-1.5 py-0.5 rounded text-xs font-mono" {...props}>
                    {children}
                  </code>
                );
              },
              // 表格样式
              table({ children }) {
                return (
                  <div className="overflow-x-auto my-2">
                    <table className="min-w-full text-xs border-collapse border border-gray-200">
                      {children}
                    </table>
                  </div>
                );
              },
              thead({ children }) {
                return <thead className="bg-gray-50">{children}</thead>;
              },
              th({ children }) {
                return <th className="border border-gray-200 px-2 py-1 text-left font-semibold text-gray-700">{children}</th>;
              },
              td({ children }) {
                return <td className="border border-gray-200 px-2 py-1 text-gray-600">{children}</td>;
              },
              // 列表样式
              ul({ children }) {
                return <ul className="list-disc pl-4 my-1 space-y-0.5">{children}</ul>;
              },
              ol({ children }) {
                return <ol className="list-decimal pl-4 my-1 space-y-0.5">{children}</ol>;
              },
              // 标题样式
              h1({ children }) { return <h1 className="text-base font-bold mt-3 mb-1">{children}</h1>; },
              h2({ children }) { return <h2 className="text-sm font-bold mt-2 mb-1">{children}</h2>; },
              h3({ children }) { return <h3 className="text-sm font-semibold mt-2 mb-0.5">{children}</h3>; },
              // 段落
              p({ children }) { return <p className="my-1">{children}</p>; },
              // 链接
              a({ href, children }) {
                return <a href={href} target="_blank" rel="noopener noreferrer" className="text-blue-600 underline hover:text-blue-800">{children}</a>;
              },
              // 引用块
              blockquote({ children }) {
                return <blockquote className="border-l-3 border-gray-300 pl-3 my-2 text-gray-600 italic">{children}</blockquote>;
              },
              // 分割线
              hr() { return <hr className="my-2 border-gray-200" />; },
            }}
          >
            {msg.text}
          </ReactMarkdown>
        </div>
        {msg.isStreaming && <span className="inline-block w-1.5 h-4 bg-blue-500 animate-pulse ml-0.5 align-text-bottom" />}
      </div>
    );
  };

  return (
    <>
      {/* Trigger Button */}
      <button
        onClick={() => setIsOpen(true)}
        className={`fixed bottom-8 right-8 z-50 p-4 bg-gradient-to-r from-blue-600 to-indigo-600 rounded-full text-white shadow-lg hover:shadow-2xl hover:scale-110 transition-all duration-300 ${isOpen ? 'scale-0 opacity-0' : 'scale-100 opacity-100'}`}
      >
        <MessageSquare className="h-6 w-6" />
      </button>

      {/* Chat Window */}
      <div
        className={`fixed bottom-8 right-8 z-50 w-96 max-w-[calc(100vw-2rem)] bg-white rounded-2xl shadow-2xl border border-gray-100 transition-all duration-300 transform origin-bottom-right flex flex-col overflow-hidden ${
          isOpen ? 'scale-100 opacity-100 translate-y-0' : 'scale-90 opacity-0 translate-y-12 pointer-events-none'
        }`}
        style={{ height: '600px', maxHeight: '80vh' }}
      >
        {/* Header */}
        <div className="bg-gradient-to-r from-blue-600 to-indigo-600 p-4 flex justify-between items-center text-white flex-shrink-0">
          <div className="flex items-center space-x-2">
            <div className="bg-white/20 p-1.5 rounded-lg">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm">智行客服</h3>
              <p className="text-xs text-blue-100">AI Agent 驱动</p>
            </div>
          </div>
          <button onClick={() => setIsOpen(false)} className="hover:bg-white/20 p-1 rounded-full transition-colors">
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-gray-50">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              {renderMessage(msg)}
            </div>
          ))}
          {isLoading && !streamingMsgIdRef.current && (
            <div className="flex justify-start">
              <div className="bg-white border border-gray-100 rounded-2xl rounded-tl-none px-4 py-3 shadow-sm flex items-center space-x-2">
                <Loader2 className="h-4 w-4 animate-spin text-blue-500" />
                <span className="text-xs text-gray-400">思考中...</span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <div className="p-4 bg-white border-t border-gray-100 flex-shrink-0">
          <div className="flex items-center space-x-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder="咨询退票、改签或行程..."
              className="flex-1 px-4 py-2 bg-gray-100 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-100 text-sm"
              disabled={isLoading}
            />
            <button
              onClick={handleSend}
              disabled={!input.trim() || isLoading}
              className="p-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <Send className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </>
  );
};

export default AIAssistant;
