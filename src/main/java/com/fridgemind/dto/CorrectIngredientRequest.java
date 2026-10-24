package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CorrectIngredientRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull(message = "分类不能为空")
    private Integer categoryId;

    @Size(max = 200)
    private String remark;
}
