package com.fridgemind.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 食谱 VO（推荐卡片 + 浏览 + 详情共用）
 * ingredients/steps 从 MySQL 全文解析；imageUrl 由后端拼接，前端直接用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeVO {

    /** 内容哈希键，详情/图片 URL 的标识 */
    private String recipeCode;
    private String name;
    private String description;
    /** 成品图 URL（/recipe-images/{code}/images/main.jpg） */
    private String imageUrl;
    private String taste;
    private String method;
    private String difficulty;
    /** 原文耗时（如"十分钟"），展示用 */
    private String cookingTime;
    /** 分钟数（可能为 null=未知），筛选用 */
    private Integer cookingTimeMin;
    private String category;
    private List<String> tags;
    /** 食材清单 [name, amount, group] */
    private List<IngredientItem> ingredients;
    /** 步骤清单 [image, text] */
    private List<StepItem> steps;

    /** 推荐场景：冰箱已有食材数 */
    private Integer haveCount;
    /** 推荐场景：缺少食材数 */
    private Integer missingCount;
    /** 推荐场景：冰箱已有食材明细 */
    private List<IngredientItem> haveIngredients;
    /** 推荐场景：缺少食材明细（详情页可一键加入购物清单） */
    private List<IngredientItem> missingIngredients;
    /** 推荐场景：命中库存中的临期食材名（⚡消耗临期标签） */
    private List<String> useExpiring;
    /** 当前家庭是否已收藏（详情页 ★ 状态） */
    private Boolean favorited;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientItem {
        private String name;
        private String amount;
        private String group;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepItem {
        /** 步骤图相对路径 images/stepNN.jpg */
        private String image;
        private String text;
    }

    /** 分类统计（分类 Tab） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryCount {
        private String category;
        private Long count;
    }
}
