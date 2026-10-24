package com.fridgemind.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IngredientDetailVO {
    private Long id;
    private String name;
    private String categoryName;
    private Integer categoryId;
    private BigDecimal quantity;
    private String unit;
    /** 状态：1=未开封, 2=已开封, 3=过期, 4=已用完 */
    private Integer status;
    private LocalDate expiryDate;
    private LocalDate productionDate;
    private String recognitionMode;
    private BigDecimal confidence;
    private String originalName;
    private String remark;
    private LocalDateTime correctedAt;
    private String correctedByName;
    private List<InventoryLogVO> recentLogs;
}
