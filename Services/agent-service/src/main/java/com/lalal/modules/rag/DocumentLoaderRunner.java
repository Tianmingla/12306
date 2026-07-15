package com.lalal.modules.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动加载知识库文档到向量存储
 *
 * 通过配置开关控制是否在启动时加载：
 * agent.rag.auto-load=true  — 启动时自动加载（默认关闭）
 * agent.rag.auto-load=false — 不自动加载
 *
 * 建议仅在首次部署或需要重建索引时开启，避免每次启动重复加载
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.auto-load", havingValue = "true", matchIfMissing = false)
public class DocumentLoaderRunner implements CommandLineRunner {

    private final DocumentEtlService documentEtlService;

    @Override
    public void run(String... args) {
        log.info("=== RAG Knowledge Base Auto-Load Started ===");
        log.info("Tip: Set agent.rag.auto-load=false to skip auto-loading on startup");
        int count = documentEtlService.loadKnowledgeDocuments();
        log.info("=== RAG Knowledge Base Auto-Load Complete: {} document chunks loaded ===", count);
    }
}
