package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.AiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 助手控制器（Ollama + Qdrant RAG）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /** AI 对话 */
    @PostMapping("/chat")
    public Result<?> chat(@RequestBody ChatRequest req) {
        return Result.success(Map.of("reply", aiService.chat(
                AuthContext.getUserId(), AuthContext.getFamilyId(), req.getMessage())));
    }

    /** 近期对话历史（单时间线，按用户隔离） */
    @GetMapping("/history")
    public Result<?> history() {
        return Result.success(aiService.history(AuthContext.getUserId()));
    }

    /** 清空对话历史 */
    @DeleteMapping("/history")
    public Result<?> clearHistory() {
        aiService.clearHistory(AuthContext.getUserId());
        return Result.success();
    }

    /** 推荐条数上限：前端最多用 10，留一倍余量；同时挡住负数（会导致 subList 越界 500） */
    private static final int MAX_RECIPE_LIMIT = 20;

    /** AI 推荐菜谱；limit 为推荐条数（购物清单页用 5），refresh 为 true 时跳过缓存强制重算（"换一批"），expiringOnly 为 true 时只出带⚡临期食材的菜（"先吃临期"入口） */
    @GetMapping("/recipe")
    public Result<?> recipe(@RequestParam(defaultValue = "10") int limit,
                            @RequestParam(defaultValue = "false") boolean refresh,
                            @RequestParam(defaultValue = "false") boolean expiringOnly) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECIPE_LIMIT);
        return Result.success(aiService.recommendRecipes(
                AuthContext.getFamilyId(), safeLimit, refresh, expiringOnly));
    }

    @Data
    static class ChatRequest {
        private String message;
    }
}
