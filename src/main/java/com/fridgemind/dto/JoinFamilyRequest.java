package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinFamilyRequest {
    @NotBlank(message = "二维码 token 不能为空")
    private String qrToken;
}
