package com.lalal.modules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI Agent 智能客服服务启动类
 * 基于 Spring AI + ReAct 模式，支持多轮对话、RAG知识库检索、工具调用
 *
 * exclude: 禁用 PGvector starter 的自动配置，
 * 因为主数据源是MySQL，PGvector使用独立的PostgreSQL数据源（PgVectorDataSourceConfig 手动构建）
 */
@SpringBootApplication(exclude = {
        org.springframework.ai.vectorstore.pgvector.PgVectorStoreAutoConfiguration.class
})
@EnableFeignClients
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
