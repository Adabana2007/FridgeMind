package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家庭食谱收藏（共享收藏池：谁收藏的大家都看到、都能点） */
@Data
@TableName("recipe_favorite")
public class RecipeFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String recipeCode;
    /** 收藏人 */
    private Long userId;
    private LocalDateTime createdAt;
}
