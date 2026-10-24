package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志 VO（含操作人名称）
 */
@Data
@Builder
public class AuditLogVO {
    private Long id;
    private Long userId;
    private String userName;
    private String action;
    private String target;
    private String level;
    private String result;
    private String ip;
    private LocalDateTime createdAt;
}
