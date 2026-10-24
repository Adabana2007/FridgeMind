package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shopping_list")
public class ShoppingList {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String name;
    private BigDecimal quantity;
    private String unit;
    private Integer categoryId;
    /** 是否已购买：0=未购买, 1=已购买 */
    private Integer isPurchased;
    private Long addedBy;
    private Long checkedBy;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
}
