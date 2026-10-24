package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 食材分类实体
 */
@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    /** 父分类ID（支持二级分类） */
    private Integer parentId;

    /** 所属家庭ID（NULL=系统预置分类） */
    private Long familyId;

    /** 排序权重，越小越靠前 */
    private Integer sortOrder;

    /** 是否系统预置：0=自定义, 1=系统预置 */
    private Integer isSystem;

    private Integer remindDays;
}
