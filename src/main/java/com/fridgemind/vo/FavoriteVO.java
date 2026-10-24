package com.fridgemind.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 收藏列表 VO（"只看收藏"视图 + 点餐弹窗横滑区） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteVO {
    private String recipeCode;
    private String name;
    private String imageUrl;
    private String difficulty;
    private String cookingTime;
    /** 收藏人昵称 */
    private String favoriteBy;
    private LocalDateTime createdAt;
}
