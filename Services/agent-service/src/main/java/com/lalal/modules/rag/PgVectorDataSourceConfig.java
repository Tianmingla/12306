package com.lalal.modules.rag;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PGvector 双数据源配置
 *
 * 问题：Spring AI PGvector starter 默认读 spring.datasource 作为数据源，
 * 但我们主数据源是 MySQL，PGvector 需要独立的 PostgreSQL 连接。
 *
 * 解决方案：手动创建 PostgreSQL DataSource + JdbcTemplate + PgVectorStore Bean，
 * 与 MySQL 主数据源完全隔离。
 *
 * 重要：pgvectorDataSource 不注册为 Spring Bean（不加 @Bean），
 * 避免干扰 MyBatis-Plus 的 SqlSessionFactory 自动配置（否则 MyBatis
 * 可能错误地绑定到 PostgreSQL 数据源，导致 t_agent_memory 查询失败）。
 * 仅暴露 JdbcTemplate 和 VectorStore 给 Spring 容器。
 *
 * EmbeddingModel 使用 Ollama 本地模型(nomic-embed-text)，
 * 不消耗 SSNAI 云端 API 额度，本地运行速度快。
 *
 * 配置项在 application.yml 的 pgvector.datasource 命名空间下，
 * 请填写你的 PostgreSQL 连接信息。
 */
@Slf4j
@Configuration
public class PgVectorDataSourceConfig {

    @Value("${pgvector.datasource.url:jdbc:postgresql://localhost:5432/rag12306}")
    private String pgUrl;

    @Value("${pgvector.datasource.username:postgres}")
    private String pgUsername;

    @Value("${pgvector.datasource.password:your-password-here}")
    private String pgPassword;
    @Value("${spring.ai.ollama.embedding.dimensions}")
    private Integer dimensions;

    /**
     * 创建 PGvector 专用的 PostgreSQL 数据源（不注册为 Spring Bean）
     * 独立于主 MySQL 数据源，避免干扰 MyBatis-Plus 的数据源绑定
     */
    private DataSource createPgvectorDataSource() {
        log.info("Initializing PGvector PostgreSQL DataSource: {}", pgUrl);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(pgUrl);
        dataSource.setUsername(pgUsername);
        dataSource.setPassword(pgPassword);
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(30000);
        return dataSource;
    }

    /**
     * PGvector 专用的 JdbcTemplate
     * 内部创建 DataSource，不暴露给 Spring 容器
     */
    @Bean("pgvectorJdbcTemplate")
    public JdbcTemplate pgvectorJdbcTemplate() {
        return new JdbcTemplate(createPgvectorDataSource());
    }

    /**
     * 手动构建 PgVectorStore Bean
     * 使用独立的 PostgreSQL 数据源 + Ollama EmbeddingModel
     *
     * 注意：
     * - 禁用 PGvector starter 的自动配置，由这里手动创建 VectorStore Bean
     * - EmbeddingModel 使用 Ollama 本地模型，不消耗云端 API 额度
     * - dimensions 必须与 embedding 模型输出维度一致：
     *   nomic-embed-text = 768维, all-minilm-l6-v2 = 384维
     */
    @Bean
    public VectorStore vectorStore(@Qualifier("pgvectorJdbcTemplate")JdbcTemplate pgvectorJdbcTemplate,
                                   OllamaEmbeddingModel embeddingModel) {
        log.info("Building PgVectorStore with dedicated PostgreSQL datasource and Ollama EmbeddingModel");
        return PgVectorStore.builder(pgvectorJdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .schemaName("public")
                .vectorTableName("vector_store")
                .maxDocumentBatchSize(10000)
                .build();
    }
}
