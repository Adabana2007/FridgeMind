package com.fridgemind.dto;

import com.fridgemind.common.DateAfter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@DateAfter(before = "productionDate", after = "expiryDate", message = "保质期必须在生产日期之后")
public class AddIngredientRequest {
    @NotNull
    private Long fridgeId;

    @NotBlank(message = "食材名称不能为空")
    @Size(max = 100, message = "食材名称不能超过100字")
    private String name;

    @NotNull(message = "分类不能为空")
    private Integer categoryId;

    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.01", message = "数量必须大于0")
    private BigDecimal quantity;

    @NotBlank(message = "单位不能为空")
    private String unit;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式不正确")
    private String expiryDate;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式不正确")
    private String productionDate;

    @Size(max = 200, message = "备注不能超过200字")
    private String remark;
}
