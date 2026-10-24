package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 家庭点餐记录（一餐制：当前菜单，吃完清空重来） */
@Data
@TableName("meal_plan")
public class MealPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 点的食谱（关联 recipe.recipe_code） */
    private String recipeCode;
    /** 谁点的 */
    private Long userId;
    /** 备注：少放辣/不放葱 */
    private String note;
    private LocalDateTime createdAt;
    /** 0=当前菜单 1=已归档（吃完啦，历史可查） */
    private Integer archived;
    /** 归档时间 */
    private LocalDateTime archivedAt;
}
