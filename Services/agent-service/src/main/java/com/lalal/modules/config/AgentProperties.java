package com.lalal.modules.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Agent 自定义配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private ModelConfig model = new ModelConfig();
    private ComplexityConfig complexity = new ComplexityConfig();
    private MemoryConfig memory = new MemoryConfig();
    private RagConfig rag = new RagConfig();
    private AgentConfig agent = new AgentConfig();
    private SseConfig sse = new SseConfig();

    @Data
    public static class ModelConfig {
        /** 简单问答模型名称 */
        private String simple = "ollama";
        /** 复杂推理模型名称 */
        private String complex = "openai";
    }

    @Data
    public static class ComplexityConfig {
        /** 复杂问题关键词 */
        private List<String> complexKeywords = Arrays.asList(
                "查询", "搜索", "购买", "购票", "退票", "改签", "订单",
                "换乘", "推荐", "帮我", "我要", "请帮我", "查一下", "搜索一下"
        );
        /** 简单问题关键词 */
        private List<String> simpleKeywords = Arrays.asList(
                "什么是", "怎么", "如何", "规则", "政策", "规定",
                "须知", "能不能", "可以吗", "区别", "说明"
        );
    }

    @Data
    public static class MemoryConfig {
        /** 单次对话最大消息数 */
        private int maxMessages = 50;
        /** Redis记忆过期时间(秒) */
        private long redisTtl = 86400;
        /** 是否启用MySQL持久化 */
        private boolean mysqlEnabled = true;
    }

    @Data
    public static class RagConfig {
        /** 检索Top-K文档 */
        private int topK = 5;
        /** 相似度阈值 */
        private double similarityThreshold = 0.5;
    }

    @Data
    public static class AgentConfig {
        /** ReAct最大步骤数 */
        private int maxSteps = 10;
        /** 单步超时(ms) */
        private long stepTimeout = 30000;
        /** 是否启用人工确认 */
        private boolean humanInTheLoop = true;
        /** 需要人工确认的操作 */
        private List<String> confirmActions = Arrays.asList(
                "purchase_ticket", "refund_ticket", "cancel_order", "change_ticket"
        );
    }

    @Data
    public static class SseConfig {
        /** SSE连接超时(ms) */
        private long timeout = 300000;
    }
}
