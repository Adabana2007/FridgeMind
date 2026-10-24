package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 看板聚合响应 VO — 一次返回首页所需的全部数据
 */
@Data
@Builder
public class DashboardStatsVO {

    private Stats stats;
    private TodayStats todayStats;
    private List<IngredientVO> expiredItems;
    private List<IngredientVO> todayItems;
    private List<IngredientVO> expiringSoonItems;
    private List<TrendPoint> trend;
    private List<CategoryStat> categoryDistribution;
    private List<WeeklyTopItem> weeklyTopConsumed;

    @Data
    @Builder
    public static class Stats {
        private int ingredientCount;
        private int fridgeCount;
        private int expiringCount;
        private int todayChanges;
        private int weeklyConsumption;
    }

    @Data
    @Builder
    public static class TodayStats {
        private int adds;
        private int consumes;
        private int newExpiring;
    }

    @Data
    @Builder
    public static class TrendPoint {
        private String date;
        private int adds;
        private int consumes;
    }

    @Data
    @Builder
    public static class CategoryStat {
        private String name;
        private int count;
    }

    @Data
    @Builder
    public static class WeeklyTopItem {
        private String ingredientName;
        private String categoryName;
        private double quantity;
        private String unit;
    }
}
