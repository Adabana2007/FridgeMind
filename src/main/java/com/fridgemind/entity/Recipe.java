package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 食谱实体（爬虫数据，recipe_code 为内容哈希幂等键） */
@Data
@TableName("recipe")
public class Recipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 内容哈希幂等键 rc_xxxxxxxxxxxx */
    private String recipeCode;
    private String name;
    private String description;
    /** 主图相对路径 images/main.jpg */
    private String imagePath;
    private String taste;
    private String method;
    private String difficulty;
    /** 原文耗时：十分钟/一小时/廿分钟 */
    private String cookingTime;
    /** 分钟数，未知为 null */
    private Integer cookingTimeMin;
    /** 食材 JSON 字符串 [{"name","amount","group"}] */
    private String ingredients;
    /** 步骤 JSON 字符串 [{"image","text"}] */
    private String steps;
    /** 标签 JSON 字符串 ["面食","早餐","饼"] */
    private String tags;
    private String category;
    private LocalDateTime createdAt;
}
