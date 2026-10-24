package com.fridgemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交反馈请求
 */
@Data
public class SubmitFeedbackRequest {
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 500, message = "反馈内容最长500字")
    private String content;
}
