package com.lalal.modules.controller;

import com.lalal.modules.dto.FeignResult;
import com.lalal.modules.rag.DocumentEtlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库管理控制器
 * 提供文档加载触发和检索测试接口
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentEtlService documentEtlService;
    private final VectorStore vectorStore;

    /**
     * 手动触发文档加载
     */
    @PostMapping("/reload")
    public FeignResult reloadDocuments() {
        log.info("Manual RAG document reload triggered");
        int count = documentEtlService.loadKnowledgeDocuments();
        return FeignResult.success("加载完成，共 " + count + " 个文档块");
    }

    /**
     * 检索测试：查询相关文档
     */
    @GetMapping("/search")
    public FeignResult searchDocuments(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        log.info("RAG search test: query={}, topK={}", query, topK);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        List<String> contents = results.stream()
                .map(doc -> {
                    String content = doc.getText();
                    String category = String.valueOf(doc.getMetadata().get("category"));
                    String filename = String.valueOf(doc.getMetadata().get("filename"));
                    return String.format("[%s | %s] %s", category, filename,
                            content.length() > 200 ? content.substring(0, 200) + "..." : content);
                })
                .collect(Collectors.toList());

        return FeignResult.success(contents);
    }
}
