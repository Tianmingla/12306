package com.lalal.modules.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档 ETL 服务
 * 负责将 docs/rag/ 目录下的文档加载、切分、元信息标注、写入向量存储
 *
 * ETL 流程：
 * 1. Extract: TextReader 读取 .txt 文件
 * 2. Transform: TokenTextSplitter 切分 + KeywordMetadataEnricher 关键词标注
 * 3. Load: VectorStore(PGvector) 写入向量存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEtlService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final KeywordMetadataEnricher keywordMetadataEnricher;

    /**
     * 加载 knowledge/ 目录下所有 .txt 文档到向量存储
     * 应用启动时自动调用（通过 DocumentLoaderRunner）
     */
    public int loadKnowledgeDocuments() {
        log.info("Starting to load knowledge documents...");
        List<Document> allDocuments = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:knowledge/**/*.txt");

            for (Resource resource : resources) {
                try {
                    String filename = resource.getFilename();
                    log.info("Loading document: {}", filename);

                    // 1. Extract — 读取文本文件
                    TextReader textReader = new TextReader(resource);
                    textReader.getCustomMetadata().put("filename", filename);

                    // 自动标注文档类别
                    String category = inferCategory(filename);
                    textReader.getCustomMetadata().put("category", category);
                    textReader.getCustomMetadata().put("source", "12306-knowledge-base");

                    List<Document> documents = textReader.read();
                    allDocuments.addAll(documents);

                } catch (Exception e) {
                    log.error("Failed to load document: {}", resource.getFilename(), e);
                }
            }

            if (allDocuments.isEmpty()) {
                log.warn("No knowledge documents found in classpath:knowledge/");
                return 0;
            }

            // 2. Transform — 切分文档
            List<Document> splitDocuments = tokenTextSplitter.apply(allDocuments);
            log.info("Split {} documents into {} chunks", allDocuments.size(), splitDocuments.size());

            // 3. Transform — 关键词元信息标注
            List<Document> enrichedDocuments = keywordMetadataEnricher.apply(splitDocuments);
            log.info("Enriched {} document chunks with keywords", enrichedDocuments.size());

            // 4. Load — 写入向量存储
            vectorStore.add(enrichedDocuments);
            log.info("Successfully loaded {} document chunks into VectorStore", enrichedDocuments.size());

            return enrichedDocuments.size();

        } catch (IOException e) {
            log.error("Failed to resolve knowledge document resources", e);
            return 0;
        }
    }

    /**
     * 手动加载指定路径的文档（用于后续动态添加文档）
     */
    public int loadDocument(Resource resource, String category) {
        try {
            TextReader textReader = new TextReader(resource);
            textReader.getCustomMetadata().put("filename", resource.getFilename());
            textReader.getCustomMetadata().put("category", category != null ? category : inferCategory(resource.getFilename()));
            textReader.getCustomMetadata().put("source", "manual-upload");

            List<Document> documents = textReader.read();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);
            List<Document> enrichedDocuments = keywordMetadataEnricher.apply(splitDocuments);

            vectorStore.add(enrichedDocuments);
            log.info("Loaded {} chunks from {}", enrichedDocuments.size(), resource.getFilename());
            return enrichedDocuments.size();
        } catch (Exception e) {
            log.error("Failed to load document: {}", resource.getFilename(), e);
            return 0;
        }
    }

    /**
     * 根据文件名推断文档类别
     */
    private String inferCategory(String filename) {
        if (filename == null) return "其他";
        String name = filename.toLowerCase();
        if (name.contains("refund") || name.contains("退") || name.contains("改签")) return "退改签政策";
        if (name.contains("purchase") || name.contains("购票") || name.contains("买票")) return "购票规则";
        if (name.contains("travel") || name.contains("乘车") || name.contains("安检")) return "乘车须知";
        if (name.contains("faq") || name.contains("常见") || name.contains("问题")) return "常见问题";
        if (name.contains("train") || name.contains("列车") || name.contains("车次")) return "列车类型";
        if (name.contains("waitlist") || name.contains("候补")) return "候补规则";
        if (name.contains("member") || name.contains("积分") || name.contains("会员")) return "会员积分";
        return "其他";
    }
}
