package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 短信监控统计
 */
@Data
@Builder
public class AdminSmsStatsVO {
    private long todayCount;
    private long monthCount;
    private double estimatedCost;
    private List<SmsAlert> alerts;

    @Data
    @Builder
    public static class SmsAlert {
        private String phone;
        private long count;
        private String lastTime;
    }
}
