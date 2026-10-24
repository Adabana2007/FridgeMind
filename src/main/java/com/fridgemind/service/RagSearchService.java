package com.fridgemind.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * RAG 语义检索服务：Spring AI VectorStore 之上的薄封装，供食谱混合搜索与推荐链路复用。
 * 分类过滤统一收敛在本层（filter DSL 的拼装细节不外泄）。
 */
public interface RagSearchService {

    /** 语义检索：query 为检索文本，topK 为候选条数，category 非空时按食谱分类过滤 */
    List<Document> search(String query, int topK, String category);
}
