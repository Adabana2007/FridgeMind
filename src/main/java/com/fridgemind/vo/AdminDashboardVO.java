package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端仪表盘统计
 */
@Data
@Builder
public class AdminDashboardVO {
    private long totalUsers;
    private long totalFamilies;
    private long totalDevices;
    private long activeDevices;
    private long faultDevices;
    private long pendingFeedbacks;
    private long todaySms;
    private long todayAuditLogs;
}
