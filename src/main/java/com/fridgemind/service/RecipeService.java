package com.fridgemind.service;

import com.fridgemind.entity.Ingredient;
import com.fridgemind.vo.FavoriteVO;
import com.fridgemind.vo.PageResult;
import com.fridgemind.vo.RecipeVO;
import com.fridgemind.vo.SearchResultVO;

import java.util.List;

/** 食谱服务：浏览/详情/分类/收藏/搜索（只读数据，权威数据源为 MySQL recipe 表） */
public interface RecipeService {

    /** 分页浏览：category/difficulty/maxMin/keyword 均可为空（走 idx_category 索引）
     *  @param maxMin 耗时上限（分钟），null 不过滤
     *  @param keyword 菜名模糊搜索，null 不过滤 */
    PageResult<RecipeVO> page(String category, String difficulty, Integer maxMin, String keyword, int page, int size);

    /** 详情（含食材/步骤全文） */
    RecipeVO detail(String code);

    /** 详情 + 冰箱库存比对（have/missing/useExpiring 与推荐卡片一致；家庭无库存时只返回纯详情） */
    RecipeVO detailWithStock(String code, Long familyId);

    /** 各分类名称与数量（分类 Tab） */
    List<RecipeVO.CategoryCount> categories();

    /** 按 recipe_code 批量回查全量（RAG 推荐链路：Qdrant 命中 → MySQL 补全文，顺序保持入参顺序） */
    List<RecipeVO> listByCodes(List<String> codes);

    /** 库存匹配：填充 have/missing 明细与 ⚡临期命中（匹配规则与 AI 服务一致，见 IngredientMatcher） */
    void fillStockMatch(List<RecipeVO> recipes, List<Ingredient> stock);

    /** 按家庭库存批量匹配（内部只取一次库存 → 逐菜填充，避免调用方 N+1 查询） */
    void fillStockMatchByFamily(List<RecipeVO> recipes, Long familyId);

    /** 混合搜索：LIKE 命中足量走关键词，否则 bge-m3/Qdrant 语义兜底（"辣的"/"清淡的"这类词不达意的查询）
     *  两条分支都按 familyId 补库存比对，搜索结果卡片才能显示"缺几样/⚡临期" */
    SearchResultVO search(String keyword, String category, Long familyId, int page, int size);

    /** 收藏食谱（幂等：家庭已收藏时直接返回） */
    void favorite(Long familyId, Long userId, String recipeCode);

    /** 取消收藏（幂等：未收藏时直接返回） */
    void unfavorite(Long familyId, String recipeCode);

    /** 家庭收藏列表（含收藏人昵称） */
    List<FavoriteVO> favorites(Long familyId);
}
