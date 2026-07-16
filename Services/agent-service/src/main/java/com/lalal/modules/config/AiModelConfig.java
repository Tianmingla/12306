package com.lalal.modules.config;

import com.lalal.modules.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模型配置
 * 双模型架构 + @Tool 工具注册：
 * 1. SSNAI OpenAI兼容API — 主力模型，处理复杂推理和工具调用
 * 2. Ollama本地模型(qwen2.5:3b) — 处理简单问答，降本60%
 *
 * 注意：
 * - ChatMemory Advisor 注册为 defaultAdvisor，conversationId 在调用时通过 advisors param 传入
 * - 工具通过 ToolRegistry 集中注册，仅 complexChatClient 需要工具能力
 */
@Configuration
public class AiModelConfig {

    /**
     * 主力 ChatClient — SSNAI OpenAI兼容API
     * 注册所有业务工具，具备工具调用能力
     */
    @Bean
    @Primary
    public ChatClient complexChatClient(OpenAiChatModel openAiChatModel,
                                        ToolRegistry toolRegistry,
                                        QuestionAnswerAdvisor questionAnswerAdvisor,
                                        ChatMemory chatMemory) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem("""
                    你是12306铁路客服智能助手"智行"，专门帮助用户处理铁路购票相关问题。

                    ## 你的能力范围
                    1. 车次查询：根据出发地、目的地、日期搜索可用车次
                    2. 购票协助：帮助用户购买车票（需确认后执行）
                    3. 订单管理：查询订单详情、退票、改签
                    4. 换乘推荐：推荐最优换乘方案
                    5. 候补下单：帮助用户候补购票
                    6. 政策解答：退改签政策、购票规则、乘车须知
                    7. 乘车人管理：查询用户的乘车人信息

                    ## 行为规范
                    - 回答使用中文，态度友好专业
                    - 需要执行具体操作时，调用相应工具，不要编造数据
                    - 涉及购票、退票、改签等敏感操作，必须先向用户确认
                    - 如果用户信息不完整，主动询问补充（如缺少乘车日期、出发地等）
                    - 不确定的信息，明确告知用户，不要猜测
                    - 退票手续费规则：开车前8天以上免手续费，48小时至8天收5%，24小时至48小时收10%，不足24小时收20%

                    ## 执行策略（Step 7 分层智能体）
                    - 对于复杂任务（如"帮我规划行程"），先向用户说明执行计划，再逐步调用工具
                    - 每次最多执行10个步骤，避免无限循环
                    - 如果发现重复调用同一工具没有新结果，立即停止并告知用户
                    - 敏感操作（退票/改签/购票/取消）执行前必须获得用户明确确认
                    - 如果工具调用失败，不要反复重试，而是告知用户并建议替代方案
                    """)

                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        questionAnswerAdvisor
                )
                .defaultToolCallbacks(toolRegistry.getToolCallbackProvider())
                .build();
    }

    /**
     * 轻量 ChatClient — Ollama本地模型
     * 不注册工具，仅处理简单常识问答
     */
    @Bean("simpleChatClient")
    public ChatClient simpleChatClient(OllamaChatModel ollamaChatModel,
                                       ChatMemory chatMemory) {
        return ChatClient.builder(ollamaChatModel)
                .defaultSystem("""
                    你是12306铁路客服智能助手"智行"的轻量版本，负责回答简单的铁路常识问题。

                    ## 回答范围
                    - 购票规则（实名制、儿童票、学生票等）
                    - 退改签政策（手续费、时限等）
                    - 乘车须知（安检、证件等）
                    - 列车类型说明（G/D/C/Z/T/K区别）

                    ## 行为规范
                    - 回答使用中文，简洁明了
                    - 如果问题涉及实时数据查询或需要执行操作，请告知用户需要转接高级模式
                    - 不确定的信息，明确告知用户
                    """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
