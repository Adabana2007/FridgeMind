package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ShoppingItemVO {
    private Long id;
    private Long familyId;
    private String name;
    private BigDecimal quantity;
    private String unit;
    private Integer categoryId;
    /** 是否已购买：0=未购买, 1=已购买 */
    private Integer isPurchased;
    private String addedByName;
    private String checkedByName;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
}
