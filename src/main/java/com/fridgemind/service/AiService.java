package com.fridgemind.service;

import com.fridgemind.entity.ChatLog;
import com.fridgemind.vo.RecipeVO;

import java.util.List;

/**
 * AI 助手服务（Ollama qwen2.5 对话 + bge-m3/Qdrant 菜谱 RAG）
 */
public interface AiService {

    String chat(Long userId, Long familyId, String message);

    /** 用户近期的对话历史（最近 30 条，单时间线按用户隔离） */
    List<ChatLog> history(Long userId);

    /** 清空用户的对话历史 */
    void clearHistory(Long userId);

    /** AI 推荐菜谱：按冰箱食材分析并列出缺少食材；refresh 为 true 时跳过缓存强制重算（换一批），expiringOnly 为 true 时只出带⚡临期食材的菜 */
    List<RecipeVO> recommendRecipes(Long familyId, int limit, boolean refresh, boolean expiringOnly);
}
