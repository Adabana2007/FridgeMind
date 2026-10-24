package com.fridgemind.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 点餐记录 VO（菜谱卡片数据 + 点餐人 + 缺少食材详情，供点餐页/购物清单使用） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MealVO {
    private Long id;
    private String recipeCode;
    private String name;
    private String imageUrl;
    private String note;
    /** 点餐人昵称 */
    private String userName;
    private LocalDateTime createdAt;
    /** 归档时间（历史记录分组展示用） */
    private LocalDateTime archivedAt;
    /** 缺的食材明细（购物清单聚合用） */
    private List<RecipeVO.IngredientItem> missingIngredients;
}
