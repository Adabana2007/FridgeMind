package com.fridgemind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 远程减少食材请求
 */
@Data
public class RemoteRemoveRequest {

    @NotBlank(message = "设备序列号不能为空")
    private String deviceSn;

    /** 设备端本地条目ID（ingredient.device_item_id） */
    @NotNull(message = "设备条目ID不能为空")
    @Min(value = 1, message = "设备条目ID不合法")
    private Long itemId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 999, message = "数量过大")
    private Integer count;
}
