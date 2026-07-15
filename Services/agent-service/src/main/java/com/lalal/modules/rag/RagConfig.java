package com.lalal.modules.rag;

import com.lalal.modules.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 知识库配置
 * 配置 ETL 组件和 QuestionAnswerAdvisor
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RagConfig {

    private final AgentProperties agentProperties;

    /**
     * 文档切分器
     * 将长文档切分为适合向量检索的小块
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * 关键词元信息标注器
     * 使用 Ollama 本地模型提取关键词，不消耗云端API额度
     * 关键词提取是轻量任务，本地模型完全够用
     */
    @Bean
    public KeywordMetadataEnricher keywordMetadataEnricher(OllamaChatModel ollamaChatModel) {
        return KeywordMetadataEnricher.builder(ollamaChatModel)
                .keywordCount(5)
                .build();
    }

    /**
     * QuestionAnswerAdvisor — RAG 检索增强问答 Advisor
     * 在用户提问时自动从向量存储中检索相关文档，注入到 prompt 上下文中
     *
     * 工作流程：
     * 1. 接收用户问题
     * 2. 用用户问题从 VectorStore 检索 topK 个相关文档
     * 3. 将检索到的文档内容拼接到 prompt 中
     * 4. AI 模型基于检索到的上下文回答问题
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        int topK = agentProperties.getRag().getTopK();
        double threshold = agentProperties.getRag().getSimilarityThreshold();

        SearchRequest searchRequest = SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        log.info("Initializing QuestionAnswerAdvisor with topK={}, similarityThreshold={}", topK, threshold);

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .userTextAdvise("""
                    以下是来自12306铁路知识库的参考信息，请基于这些信息回答用户的问题。
                    如果参考信息不足以回答问题，请明确告知用户，不要编造信息。
                    回答时请引用参考信息的来源。

                    参考信息：
                    {question_answer_context}
                    """)
                .build();
    }
}
