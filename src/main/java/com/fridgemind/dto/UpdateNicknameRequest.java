package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNicknameRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称不能超过 20 字")
    private String nickname;
}
