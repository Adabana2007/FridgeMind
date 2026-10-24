package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddFridgeRequest {
    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 64)
    private String deviceSn;
}
