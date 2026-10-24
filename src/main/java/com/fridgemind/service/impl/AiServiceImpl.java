package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgemind.ai.FridgeTools;
import com.fridgemind.ai.InventoryContextAdvisor;
import com.fridgemind.ai.InventoryContextBuilder;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.ChatLog;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.mapper.ChatLogMapper;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.service.AiService;
import com.fridgemind.service.RagSearchService;
import com.fridgemind.service.RecipeService;
import com.fridgemind.vo.RecipeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** AI 服务实现（Ollama 对话 + Qdrant RAG 菜谱推荐） */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    /** RAG 检索+推理较慢，推荐结果缓存 10 分钟；库存变化时由 evictFamilyCaches 失效 */
    public static final String RECIPE_CACHE_PREFIX = "ai:recipe:";
    /** 临期池缓存后缀："先吃临期"与首页全量池分开存，互不覆盖；失效时必须一并删除 */
    public static final String EXPIRING_CACHE_SUFFIX = ":exp";
    private static final int RECIPE_CACHE_SECONDS = 600;
    /** 推荐池 12 条：缓存只存这一份，limit 在返回时截取（购物清单 5 / 移动端 10 / Web 首页 12），避免不同 limit 互相串 */
    private static final int RECIPE_POOL_SIZE = 12;
    /** 向量检索候选条数：多取候选再随机选，保证"换一批"有差异（池子 12 → 最多取 24 条，40 足够） */
    private static final int RECIPE_CANDIDATE_SIZE = 40;

    /** 对话上下文记忆轮数 */
    private static final int MEMORY_ROUNDS = 6;

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;
    private final ChatLogMapper chatLogMapper;
    private final RecipeService recipeService;
    private final RagSearchService ragSearchService;
    private final InventoryContextBuilder inventoryContextBuilder;
    private final ObjectMapper objectMapper;
    /** 对话模型客户端（Spring AI 官方 ChatClient：库存 Advisor 与冰箱工具在此装配，系统提示词来自资源文件） */
    private final ChatClient chatClient;
    /** 系统提示词正文（来自 classpath:prompts/ai-chat-system.st，改文案不必重新编译） */
    private final String systemPrompt;
    private final RedisTemplate<String, Object> redisTemplate;

    public AiServiceImpl(FridgeMapper fridgeMapper, IngredientMapper ingredientMapper,
                         ChatLogMapper chatLogMapper, RecipeService recipeService,
                         RagSearchService ragSearchService, InventoryContextBuilder inventoryContextBuilder,
                         ObjectMapper objectMapper, ChatClient.Builder chatClientBuilder,
                         InventoryContextAdvisor inventoryContextAdvisor, FridgeTools fridgeTools,
                         @Value("classpath:prompts/ai-chat-system.st") Resource systemPromptResource,
                         RedisTemplate<String, Object> redisTemplate) throws IOException {
        this.fridgeMapper = fridgeMapper;
        this.ingredientMapper = ingredientMapper;
        this.chatLogMapper = chatLogMapper;
        this.recipeService = recipeService;
        this.ragSearchService = ragSearchService;
        this.inventoryContextBuilder = inventoryContextBuilder;
        this.objectMapper = objectMapper;
        this.systemPrompt = StreamUtils.copyToString(systemPromptResource.getInputStream(), StandardCharsets.UTF_8).trim();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(inventoryContextAdvisor)
                .defaultTools(fridgeTools)
                .build();
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String chat(Long userId, Long familyId, String message) {
        log.info("AI 对话 userId={}, message={}", userId, message);

        // 最近 MEMORY_ROUNDS 轮成对消息作为上下文，让 AI 记得前面聊过什么
        List<Message> history = new ArrayList<>();
        List<ChatLog> recent = chatLogMapper.selectRecent(userId, MEMORY_ROUNDS * 2);
        Collections.reverse(recent); // 倒序取出后转正序喂给模型
        for (ChatLog log : recent) {
            history.add(log.getRole() == 1
                    ? new AssistantMessage(log.getContent())
                    : new UserMessage(log.getContent()));
        }

        String inventoryContext = inventoryContextBuilder.build(familyId);
        String prompt = inventoryContext.isEmpty()
                ? systemPrompt + "\n用户目前还没有添加冰箱或食材。"
                : systemPrompt;

        try {
            String reply = chatClient.prompt()
                    .system(prompt)
                    .messages(history)
                    .user(message)
                    // 库存片段经官方 Advisor 链并入系统提示词（Advisor 无状态，片段按请求传入）
                    .advisors(advisor -> advisor.param(
                            InventoryContextAdvisor.INVENTORY_CONTEXT_KEY, inventoryContext))
                    .call()
                    .content();
            // 成功才落库：失败的对话不进历史，重试时上下文保持干净
            saveMessage(userId, 0, message);
            saveMessage(userId, 1, reply);
            return reply;
        } catch (Exception e) {
            log.error("Ollama Chat 调用失败", e);
            return "抱歉，AI 服务暂时不可用，请检查 Ollama 是否正在运行。";
        }
    }

    @Override
    public List<ChatLog> history(Long userId) {
        List<ChatLog> recent = chatLogMapper.selectRecent(userId, 30);
        Collections.reverse(recent); // 时间正序返回给页面展示
        return recent;
    }

    @Override
    public void clearHistory(Long userId) {
        chatLogMapper.delete(Wrappers.<ChatLog>lambdaQuery()
                .eq(ChatLog::getUserId, userId));
    }

    private void saveMessage(Long userId, int role, String content) {
        ChatLog log = new ChatLog();
        log.setUserId(userId);
        log.setRole(role);
        log.setContent(content);
        log.setCreatedAt(LocalDateTime.now());
        chatLogMapper.insert(log);
    }

    @Override
    public List<RecipeVO> recommendRecipes(Long familyId, int limit, boolean refresh, boolean expiringOnly) {
        // 缓存 key 按模式分离：全量池与临期池互不覆盖（不同入口各看各的）
        String key = RECIPE_CACHE_PREFIX + familyId + (expiringOnly ? EXPIRING_CACHE_SUFFIX : "");
        // refresh 时跳过缓存，保证"换一批"拿到重新随机的结果
        if (!refresh) {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String json) {
                try {
                    List<RecipeVO> cachedList = objectMapper.readValue(json, new TypeReference<List<RecipeVO>>() {});
                    return cachedList.subList(0, Math.min(limit, cachedList.size()));
                } catch (Exception e) {
                    log.warn("菜谱缓存解析失败，重新计算: {}", e.getMessage());
                }
            }
        }
        // 重算时按池大小计算（缓存永远是 10 条的池子）
        List<RecipeVO> recipes = computeRecipes(familyId, RECIPE_POOL_SIZE, expiringOnly);
        // 空结果不缓存：否则冰箱刚补了食材，"AI 推荐"最长 10 分钟还是空的
        if (!recipes.isEmpty()) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(recipes),
                        RECIPE_CACHE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("菜谱缓存写入失败: {}", e.getMessage());
            }
        }
        return recipes.subList(0, Math.min(limit, recipes.size()));
    }

    /** 菜谱推荐计算（缓存未命中或换一批时执行）；expiringOnly 为 true 时只保留命中⚡临期食材的菜（"先吃临期"入口） */
    private List<RecipeVO> computeRecipes(Long familyId, int limit, boolean expiringOnly) {
        List<Fridge> fridges = fridgeMapper.selectByFamilyId(familyId);
        if (fridges.isEmpty()) {
            return List.of();
        }

        List<Long> fridgeIds = fridges.stream().map(Fridge::getId).toList();
        List<Ingredient> ingredients = sortExpiringFirst(
                ingredientMapper.selectAvailableByFridgeIds(fridgeIds));
        if (ingredients.isEmpty()) {
            return List.of();
        }

        // 临期食材排在前面，向量检索天然优先命中消耗它们的菜谱
        String query = ingredients.stream()
                .map(Ingredient::getName)
                .distinct()
                .collect(Collectors.joining("、"));
        log.info("RAG 查询文本: {}", query);

        // 取 40 条候选（编码与相似度检索由 Spring AI VectorStore 承担），后续随机选 10 保证刷新多样性
        List<Document> hits;
        try {
            hits = ragSearchService.search(query, RECIPE_CANDIDATE_SIZE, null);
        } catch (Exception e) {
            log.warn("向量检索失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "AI 服务暂时不可用，请确认 Ollama 与 Qdrant 已启动");
        }

        if (hits.isEmpty()) {
            return List.of();
        }

        // 向量库只给轻量 payload → 拿 recipe_code 回 MySQL 取全文（食材/步骤/中文耗时）
        List<String> codes = hits.stream()
                .map(doc -> (String) doc.getMetadata().get("recipe_code"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (codes.isEmpty()) {
            return List.of();
        }
        List<RecipeVO> recipes = recipeService.listByCodes(codes);

        // 临期模式顺序不同：先对全部候选做库存匹配 → 过滤⚡临期 → 再轮换取样（保证临期菜数量充足）
        List<RecipeVO> picked;
        if (expiringOnly) {
            recipeService.fillStockMatch(recipes, ingredients);
            List<RecipeVO> expiring = recipes.stream()
                    .filter(r -> r.getUseExpiring() != null && !r.getUseExpiring().isEmpty())
                    .collect(Collectors.toList());
            if (expiring.isEmpty()) {
                return List.of(); // 没有临期相关的菜（前端提示"没有临期相关的菜"）
            }
            picked = diversifyByCategory(expiring, limit);
        } else {
            // 分类轮换：候选按类别分组轮流出（每类最多 2），避免"甜点扎堆"式单一化推荐
            picked = diversifyByCategory(recipes, limit);
            // 对比冰箱已有食材 vs 菜谱所需食材（含 ⚡临期计算，规则集中在 IngredientMatcher）
            recipeService.fillStockMatch(picked, ingredients);
        }
        if (picked.size() > limit) {
            picked = picked.subList(0, limit);
        }
        return picked;
    }

    /**
     * 分类轮换取样：将候选按 category 分组并组内洗牌，按类轮流出队（每类最多 maxPerCategory 条），
     * 不足 limit 时放宽上限补齐——保证推荐里荤素汤点分布均匀
     */
    private List<RecipeVO> diversifyByCategory(List<RecipeVO> candidates, int limit) {
        int maxPerCategory = 2; // 常态限制：单类最多 2 条
        Map<String, List<RecipeVO>> byCategory = candidates.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategory() != null ? r.getCategory() : "其他",
                        Collectors.toCollection(ArrayList::new)));
        List<List<RecipeVO>> queues = byCategory.values().stream().map(q -> {
            Collections.shuffle(q); // 组内随机，保证"换一批"有差异
            return q;
        }).collect(Collectors.toList());

        List<RecipeVO> picked = new ArrayList<>();
        for (int round = 0; round < maxPerCategory && picked.size() < limit; round++) {
            for (List<RecipeVO> q : queues) {
                if (picked.size() >= limit) {
                    break;
                }
                if (!q.isEmpty()) {
                    picked.add(q.remove(0));
                }
            }
        }
        // 分类不足以凑满 limit 时放宽（兜底，不让结果比要求少）
        if (picked.size() < limit) {
            for (List<RecipeVO> q : queues) {
                if (picked.size() >= limit) {
                    break;
                }
                while (!q.isEmpty() && picked.size() < limit) {
                    picked.add(q.remove(0));
                }
            }
        }
        return picked;
    }

    /** 临期优先排序：有过期日期的按升序在前，无日期的排后 */
    private List<Ingredient> sortExpiringFirst(List<Ingredient> ingredients) {
        ingredients.sort(java.util.Comparator.comparing(
                Ingredient::getExpiryDate,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return ingredients;
    }
}
