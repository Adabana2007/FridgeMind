package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.IngredientMatcher;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.Recipe;
import com.fridgemind.entity.RecipeFavorite;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.RecipeFavoriteMapper;
import com.fridgemind.mapper.RecipeMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.service.RagSearchService;
import com.fridgemind.service.RecipeService;
import com.fridgemind.vo.FavoriteVO;
import com.fridgemind.vo.PageResult;
import com.fridgemind.vo.RecipeVO;
import com.fridgemind.vo.SearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 食谱服务实现（只读；权威数据源 = MySQL recipe 表） */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private final RecipeMapper recipeMapper;
    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;
    private final RecipeFavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<RecipeVO> page(String category, String difficulty, Integer maxMin, String keyword, int page, int size) {
        LambdaQueryWrapper<Recipe> qw = new LambdaQueryWrapper<Recipe>()
                .eq(category != null && !category.isBlank(), Recipe::getCategory, category)
                .eq(difficulty != null && !difficulty.isBlank(), Recipe::getDifficulty, difficulty)
                // 耗时未知(NULL，"数小时/数天")的菜不参与"X分钟内"筛选：宁可少给也不给错
                .le(maxMin != null, Recipe::getCookingTimeMin, maxMin)
                .like(keyword != null && !keyword.isBlank(), Recipe::getName, keyword)
                // created_at 大面积同值（同一批爬虫入库），必须用 id 兜底，否则翻页会重复/漏项
                .orderByDesc(Recipe::getCreatedAt)
                .orderByDesc(Recipe::getId);
        IPage<Recipe> result = recipeMapper.selectPage(new Page<>(page, size), qw);
        return new PageResult<>(
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList()),
                result.getTotal(), page, size);
    }

    @Override
    public RecipeVO detail(String code) {
        Recipe recipe = recipeMapper.selectOne(
                new LambdaQueryWrapper<Recipe>().eq(Recipe::getRecipeCode, code));
        if (recipe == null) {
            throw new BusinessException(ResultCode.RECIPE_NOT_FOUND);
        }
        return toVO(recipe);
    }

    @Override
    public RecipeVO detailWithStock(String code, Long familyId) {
        RecipeVO vo = detail(code);
        List<Ingredient> stock = loadStock(familyId);
        if (!stock.isEmpty()) {
            fillStockMatch(List.of(vo), stock);
        }
        // 收藏状态（详情页 ★ 按钮）
        vo.setFavorited(favoriteMapper.selectCount(new LambdaQueryWrapper<RecipeFavorite>()
                .eq(RecipeFavorite::getFamilyId, familyId)
                .eq(RecipeFavorite::getRecipeCode, code)) > 0);
        return vo;
    }

    @Override
    public void fillStockMatchByFamily(List<RecipeVO> recipes, Long familyId) {
        if (recipes == null || recipes.isEmpty() || familyId == null) { // 未加入家庭 = 无库存可比
            return;
        }
        List<Ingredient> stock = loadStock(familyId);
        if (!stock.isEmpty()) {
            fillStockMatch(recipes, stock);
        }
    }

    /** 家庭全量可用库存（无冰箱/无食材返回空列表） */
    private List<Ingredient> loadStock(Long familyId) {
        List<Fridge> fridges = fridgeMapper.selectByFamilyId(familyId);
        if (fridges.isEmpty()) {
            return List.of();
        }
        List<Long> fridgeIds = fridges.stream().map(Fridge::getId).toList();
        return ingredientMapper.selectAvailableByFridgeIds(fridgeIds);
    }

    @Override
    public List<RecipeVO.CategoryCount> categories() {
        return recipeMapper.countByCategory().stream()
                .map(m -> RecipeVO.CategoryCount.builder()
                        .category((String) m.get("category"))
                        .count(((Number) m.get("cnt")).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<RecipeVO> listByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, Recipe> byCode = recipeMapper.selectList(
                        new LambdaQueryWrapper<Recipe>().in(Recipe::getRecipeCode, codes))
                .stream().collect(Collectors.toMap(Recipe::getRecipeCode, Function.identity()));
        // 保持入参顺序（检索结果就是按相似度排的，顺序不能乱）
        List<RecipeVO> result = new ArrayList<>();
        for (String code : codes) {
            Recipe r = byCode.get(code);
            if (r != null) {
                result.add(toVO(r));
            }
        }
        return result;
    }

    @Override
    public void fillStockMatch(List<RecipeVO> recipes, List<com.fridgemind.entity.Ingredient> stock) {
        // ⚡临期口径：今天起 3 天内到期，不含已过期（"暂不推荐食用"的窄口径，与看板按分类 remind_days 的口径不同）
        List<String> expiringNames = stock.stream()
                .filter(i -> i.getExpiryDate() != null)
                .filter(i -> {
                    // 必须用 DAYS.between 算总天数：until() 返回 Period，getDays() 只取天数分量，跨月会算错
                    long days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), i.getExpiryDate());
                    return days >= 0 && days <= 3;
                })
                .map(com.fridgemind.entity.Ingredient::getName)
                .toList();
        List<String> stockNames = stock.stream()
                .map(i -> i.getName().toLowerCase().trim())
                .distinct()
                .collect(Collectors.toList());

        for (RecipeVO recipe : recipes) {
            List<RecipeVO.IngredientItem> have = new ArrayList<>();
            List<RecipeVO.IngredientItem> missing = new ArrayList<>();
            for (RecipeVO.IngredientItem ing : recipe.getIngredients()) {
                boolean matched = stockNames.stream()
                        .anyMatch(s -> IngredientMatcher.match(s, ing.getName()));
                (matched ? have : missing).add(ing);
            }
            recipe.setHaveIngredients(have);
            recipe.setMissingIngredients(missing);
            recipe.setHaveCount(have.size());
            recipe.setMissingCount(missing.size());

            // ⚡临期命中：这道菜用到的食材里，哪些能匹配库存中临期的食材名（去重）
            recipe.setUseExpiring(have.stream()
                    .map(RecipeVO.IngredientItem::getName)
                    .filter(name -> expiringNames.stream().anyMatch(exp -> IngredientMatcher.match(exp, name)))
                    .distinct()
                    .collect(Collectors.toList()));
        }
    }

    @Override
    public void favorite(Long familyId, Long userId, String recipeCode) {
        detail(recipeCode); // 校验食谱存在（不存在抛 RECIPE_NOT_FOUND）
        Long exists = favoriteMapper.selectCount(new LambdaQueryWrapper<RecipeFavorite>()
                .eq(RecipeFavorite::getFamilyId, familyId)
                .eq(RecipeFavorite::getRecipeCode, recipeCode));
        if (exists > 0) {
            return; // 幂等：已收藏直接返回
        }
        RecipeFavorite fav = new RecipeFavorite();
        fav.setFamilyId(familyId);
        fav.setRecipeCode(recipeCode);
        fav.setUserId(userId);
        fav.setCreatedAt(LocalDateTime.now());
        try {
            favoriteMapper.insert(fav);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.debug("收藏并发冲突，忽略: {}", e.getMessage()); // 唯一键兜底，视为已收藏
        }
    }

    @Override
    public void unfavorite(Long familyId, String recipeCode) {
        favoriteMapper.delete(new LambdaQueryWrapper<RecipeFavorite>()
                .eq(RecipeFavorite::getFamilyId, familyId)
                .eq(RecipeFavorite::getRecipeCode, recipeCode));
    }

    @Override
    public List<FavoriteVO> favorites(Long familyId) {
        List<RecipeFavorite> favs = favoriteMapper.selectList(
                new LambdaQueryWrapper<RecipeFavorite>()
                        .eq(RecipeFavorite::getFamilyId, familyId)
                        .orderByDesc(RecipeFavorite::getCreatedAt));
        if (favs.isEmpty()) {
            return List.of();
        }
        // 收藏人昵称批量查（一次 SQL，避免逐条查）
        Map<Long, String> nameMap = userMapper.selectBatchIds(
                        favs.stream().map(RecipeFavorite::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() != null ? u.getNickname() : ""));
        Map<String, RecipeVO> recipeMap = recipeMapper.selectList(
                        new LambdaQueryWrapper<Recipe>().in(Recipe::getRecipeCode,
                                favs.stream().map(RecipeFavorite::getRecipeCode).toList()))
                .stream().collect(Collectors.toMap(Recipe::getRecipeCode, this::toVO));

        return favs.stream().map(f -> {
            RecipeVO recipe = recipeMap.get(f.getRecipeCode());
            return FavoriteVO.builder()
                    .recipeCode(f.getRecipeCode())
                    .name(recipe != null ? recipe.getName() : f.getRecipeCode())
                    .imageUrl(recipe != null ? recipe.getImageUrl() : "")
                    .difficulty(recipe != null ? recipe.getDifficulty() : "")
                    .cookingTime(recipe != null ? recipe.getCookingTime() : "")
                    .favoriteBy(nameMap.getOrDefault(f.getUserId(), ""))
                    .createdAt(f.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /** LIKE 命中阈值：低于该数量说明关键词没匹配到什么，需要语义兜底 */
    private static final int LIKE_MIN_HITS = 3;
    /** 语义检索返回条数上限（一次弹窗展示量，不做分页） */
    private static final int SEMANTIC_LIMIT = 20;

    @Override
    public SearchResultVO search(String keyword, String category, Long familyId, int page, int size) {
        // 第一步：菜名 LIKE（快、准；"土豆"这类记得名字的查询永远走这条路）
        PageResult<RecipeVO> like = page(category, null, null, keyword, page, size);
        if (like.getTotal() >= LIKE_MIN_HITS) {
            List<RecipeVO> records = like.getRecords();
            fillStockMatchByFamily(records, familyId); // 搜索卡片与推荐卡片一样要显示"缺几样/⚡临期"
            return SearchResultVO.builder().type("like").records(records).total(like.getTotal()).build();
        }
        // 第二步：LIKE 命中不足 → bge-m3/Qdrant 语义兜底（"辣的"/"清淡的"这类词不达意）
        try {
            List<String> codes = ragSearchService.search(keyword, SEMANTIC_LIMIT, category).stream()
                    .map(doc -> (String) doc.getMetadata().get("recipe_code"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            List<RecipeVO> records = listByCodes(codes);
            fillStockMatchByFamily(records, familyId);
            return SearchResultVO.builder().type("semantic").records(records).total(records.size()).build();
        } catch (Exception e) {
            // 语义服务不可用时降级：返回 LIKE 的部分结果（可能为空），不阻塞选菜
            log.warn("语义搜索失败，降级为关键词结果: {}", e.getMessage());
            List<RecipeVO> records = like.getRecords();
            fillStockMatchByFamily(records, familyId);
            return SearchResultVO.builder().type("like").records(records).total(like.getTotal()).build();
        }
    }

    /** 实体 → VO：JSON 列解析 + 图片 URL 拼接（相对路径换访问路径是渲染层职责，统一在此完成） */
    private RecipeVO toVO(Recipe r) {
        return RecipeVO.builder()
                .recipeCode(r.getRecipeCode())
                .name(r.getName())
                .description(r.getDescription())
                .imageUrl("/recipe-images/" + r.getRecipeCode() + "/" + r.getImagePath())
                .taste(r.getTaste())
                .method(r.getMethod())
                .difficulty(r.getDifficulty())
                .cookingTime(r.getCookingTime())
                .cookingTimeMin(r.getCookingTimeMin())
                .category(r.getCategory())
                .tags(parseList(r.getTags(), new TypeReference<List<String>>() {}))
                .ingredients(parseList(r.getIngredients(), new TypeReference<List<RecipeVO.IngredientItem>>() {}))
                .steps(parseList(r.getSteps(), new TypeReference<List<RecipeVO.StepItem>>() {}))
                .build();
    }

    /** JSON 列解析失败不致命：返回空列表，页面降级展示（脏掉一条不影响整页） */
    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("食谱 JSON 列解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
