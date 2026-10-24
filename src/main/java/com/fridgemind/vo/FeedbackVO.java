package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈 VO（含用户名）
 */
@Data
@Builder
public class FeedbackVO {
    private Long id;
    private Long userId;
    private String userName;
    private String content;
    /** 处理状态：0=未处理, 1=已处理 */
    private Integer status;
    private String reply;
    private LocalDateTime createdAt;
}
