package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 食材实体（核心业务表）
 */
@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fridgeId;

    private Integer categoryId;

    private String name;

    private BigDecimal quantity;

    /** 单位（eg. 个/袋/瓶/盒/g/mL） */
    private String unit;

    /** 状态：1=未开封, 2=已开封, 3=过期, 4=已用完 */
    private Integer status;

    private LocalDate expiryDate;

    private LocalDate productionDate;

    /** 录入方式：manual/auto */
    private String recognitionMode;

    /** AI识别置信度（0.00~1.00） */
    private BigDecimal confidence;

    /** AI识别的原始名称（用户修改前） */
    private String originalName;

    private Long correctedBy;

    private LocalDateTime correctedAt;

    private String remark;

    private LocalDateTime createdAt;

    /** 设备端本地条目ID（端云对账用，手动录入为NULL） */
    private Long deviceItemId;

    /** 更新时间：由 MetaObjectHandler 自动填充（手写赋值会覆盖数据库的 ON UPDATE CURRENT_TIMESTAMP） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
