package com.fridgemind.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConsumeIngredientRequest {
    @NotNull
    @DecimalMin(value = "0.01", message = "消耗数量必须大于0")
    private BigDecimal quantity;

    /** 确认类型（可选）：1=已食用, 2=已丢弃, 3=已过期，不选则留待标注 */
    private Integer confirmed;
}
