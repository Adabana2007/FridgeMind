package com.fridgemind.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientVO {
    private Long id;
    private String name;
    private String categoryName;
    private Integer categoryId;
    private BigDecimal quantity;
    private String unit;
    /** 状态：1=未开封, 2=已开封, 3=过期, 4=已用完 */
    private Integer status;
    private LocalDate expiryDate;
    private Boolean expiringSoon;
    private Integer daysUntilExpiry;
    private String fridgeName;
}
