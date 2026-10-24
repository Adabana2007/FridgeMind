package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFamilyRequest {
    @NotBlank(message = "家庭名称不能为空")
    @Size(max = 50, message = "家庭名称不能超过 50 字")
    private String name;
}
