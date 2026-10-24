package com.fridgemind.service.impl;

import com.fridgemind.service.RagSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** RAG 语义检索服务实现：由 Spring AI VectorStore 承担编码与相似度检索 */
@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final VectorStore vectorStore;

    @Override
    public List<Document> search(String query, int topK, String category) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(topK);
        // 分类过滤走 payload 索引（rebuild_vectors.py 已为 category 建 keyword 索引）
        if (StringUtils.hasText(category)) {
            builder.filterExpression(new FilterExpressionBuilder().eq("category", category).build());
        }
        return vectorStore.similaritySearch(builder.build());
    }
}
