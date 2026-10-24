package com.fridgemind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 远程添加食材请求
 */
@Data
public class RemoteAddRequest {

    @NotBlank(message = "设备序列号不能为空")
    private String deviceSn;

    @NotBlank(message = "食材名称不能为空")
    private String name;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 999, message = "数量过大")
    private Integer count;

    /** 分类（可空，设备端缺省按"蔬菜水果"处理） */
    private String category;
}
