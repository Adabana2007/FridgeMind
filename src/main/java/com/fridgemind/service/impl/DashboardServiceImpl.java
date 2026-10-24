package com.fridgemind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgemind.entity.Category;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.InventoryLog;
import com.fridgemind.mapper.CategoryMapper;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.InventoryLogMapper;
import com.fridgemind.service.DashboardService;
import com.fridgemind.vo.DashboardStatsVO;
import com.fridgemind.vo.IngredientVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 看板聚合服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");
    private static final String STATS_CACHE_PREFIX = "dashboard:stats:";
    private static final int STATS_CACHE_SECONDS = 60;

    @Override
    public DashboardStatsVO stats(Long familyId, Long fridgeId) {
        if (familyId == null) {
            return emptyStats();
        }
        // 家庭聚合视图缓存 60s；指定冰箱时直算（键数量可控）
        if (fridgeId == null) {
            String key = STATS_CACHE_PREFIX + familyId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String json) {
                try {
                    return objectMapper.readValue(json, DashboardStatsVO.class);
                } catch (Exception e) {
                    log.warn("看板缓存解析失败，重新计算: {}", e.getMessage());
                }
            }
            DashboardStatsVO stats = computeStats(familyId, null);
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(stats),
                        STATS_CACHE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("看板缓存写入失败: {}", e.getMessage());
            }
            return stats;
        }
        return computeStats(familyId, fridgeId);
    }

    /** 家庭/单冰箱统计计算（缓存未命中时执行） */
    private DashboardStatsVO computeStats(Long familyId, Long fridgeId) {
        if (familyId == null) {
            return emptyStats();
        }

        // 指定冰箱则只统计该冰箱（校验归属），否则聚合整个家庭
        List<Fridge> fridges;
        if (fridgeId != null) {
            Fridge fridge = fridgeMapper.selectById(fridgeId);
            if (fridge == null || !familyId.equals(fridge.getFamilyId())) {
                return emptyStats();
            }
            fridges = List.of(fridge);
        } else {
            fridges = fridgeMapper.selectByFamilyId(familyId);
        }

        List<Long> fridgeIds = fridges.stream().map(Fridge::getId).toList();
        Map<Long, String> fridgeNameMap = fridges.stream()
                .collect(Collectors.toMap(Fridge::getId, Fridge::getName, (a, b) -> a));

        if (fridgeIds.isEmpty()) {
            return emptyStats();
        }

        // 查所有食材（排除已用完）
        List<Ingredient> allIngredients = ingredientMapper.selectByFridgeIds(fridgeIds, 4);

        Map<Integer, Category> categoryMap = loadCategoryMap(allIngredients);

        LocalDate today = LocalDate.now();
        // 近 7 天窗口：含今天共 7 个自然日
        LocalDateTime sevenDaysAgo = today.minusDays(6).atStartOfDay();

        // 构建 IngredientVO 并按过期 / 今天 / 临期分组
        List<IngredientVO> expiredItems = new ArrayList<>();
        List<IngredientVO> todayItems = new ArrayList<>();
        List<IngredientVO> expiringSoonItems = new ArrayList<>();
        List<IngredientVO> allVOs = new ArrayList<>();

        for (Ingredient ing : allIngredients) {
            Category cat = categoryMap.get(ing.getCategoryId());
            // 未设置提醒天数时默认 7 天
            int remindDays = (cat != null && cat.getRemindDays() != null) ? cat.getRemindDays() : 7;

            IngredientVO vo = toIngredientVO(ing, cat, today, remindDays, fridgeNameMap);
            allVOs.add(vo);

            // <0 已过期、=0 今天到期、>0 且到提醒线 → 三组；>0 未临期仅进全量
            if (vo.getDaysUntilExpiry() != null) {
                if (vo.getDaysUntilExpiry() < 0) {
                    expiredItems.add(vo);
                } else if (vo.getDaysUntilExpiry() == 0) {
                    todayItems.add(vo);
                } else if (Boolean.TRUE.equals(vo.getExpiringSoon())) {
                    expiringSoonItems.add(vo);
                }
            }
        }

        // 近 7 天趋势 + 今日统计 + 本周消耗
        List<InventoryLog> recentLogs = inventoryLogMapper.selectRecentByFridgeIds(fridgeIds, sevenDaysAgo);

        // LinkedHashMap 保证日期顺序
        Map<String, int[]> trendMap = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            trendMap.put(today.minusDays(i).format(DATE_FMT), new int[]{0, 0});
        }

        int todayAdds = 0, todayConsumes = 0;
        String todayKey = today.format(DATE_FMT);
        Map<Long, double[]> weeklyConsumeMap = new HashMap<>(); // ingredientId -> [totalQty, type]

        for (InventoryLog log : recentLogs) {
            if (log.getCreatedAt() == null) continue;

            String key = log.getCreatedAt().toLocalDate().format(DATE_FMT);
            int[] c = trendMap.get(key);
            if (c != null) {
                if (log.getType() == 1) c[0]++;          // 入库
                else if (log.getType() == 2 || log.getType() == 3) c[1]++; // 消耗/过期
            }

            if (todayKey.equals(key)) {
                if (log.getType() == 1) todayAdds++;
                else if (log.getType() == 2 || log.getType() == 3) todayConsumes++;
            }

            // 本周消耗累计（按食材分组）
            if (log.getType() == 2 || log.getType() == 3) {
                double qty = log.getQuantityChange() != null
                        ? Math.abs(log.getQuantityChange().doubleValue()) : 0;
                weeklyConsumeMap.merge(log.getIngredientId(),
                        new double[]{qty, 0}, (a, b) -> new double[]{a[0] + b[0], 0});
            }
        }

        List<DashboardStatsVO.TrendPoint> trend = trendMap.entrySet().stream()
                .map(e -> DashboardStatsVO.TrendPoint.builder()
                        .date(e.getKey()).adds(e.getValue()[0]).consumes(e.getValue()[1]).build())
                .collect(Collectors.toList());

        int[] tc = trendMap.getOrDefault(todayKey, new int[]{0, 0});
        int todayChanges = tc[0] + tc[1];

        // 今日概览：今日到期 = todayItems 的数量
        int todayExpiring = todayItems.size();

        // 本周消耗 Top 5
        List<DashboardStatsVO.WeeklyTopItem> weeklyTop = weeklyConsumeMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Ingredient ing = ingredientMapper.selectById(e.getKey());
                    Category cat = ing != null && ing.getCategoryId() != null
                            ? categoryMapper.selectById(ing.getCategoryId()) : null;
                    return DashboardStatsVO.WeeklyTopItem.builder()
                            .ingredientName(ing != null ? ing.getName() : "未知")
                            .categoryName(cat != null ? cat.getName() : "")
                            .quantity(e.getValue()[0])
                            .unit(ing != null ? ing.getUnit() : "")
                            .build();
                })
                .collect(Collectors.toList());

        double totalConsumed = weeklyConsumeMap.values().stream()
                .mapToDouble(v -> v[0]).sum();

        // 分类分布（按数量降序）
        Map<String, Long> categoryCount = allVOs.stream()
                .collect(Collectors.groupingBy(
                        vo -> vo.getCategoryName() != null ? vo.getCategoryName() : "未分类",
                        Collectors.counting()));
        List<DashboardStatsVO.CategoryStat> categoryDistribution = categoryCount.entrySet().stream()
                .map(e -> DashboardStatsVO.CategoryStat.builder()
                        .name(e.getKey()).count(e.getValue().intValue()).build())
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());

        // 待处理临期总数 = 已过期 + 今日到期 + 临期
        int expiringCount = expiredItems.size() + todayItems.size() + expiringSoonItems.size();

        return DashboardStatsVO.builder()
                .stats(DashboardStatsVO.Stats.builder()
                        .ingredientCount(allVOs.size())
                        .fridgeCount(fridges.size())
                        .expiringCount(expiringCount)
                        .todayChanges(todayChanges)
                        .weeklyConsumption((int) totalConsumed)
                        .build())
                .todayStats(DashboardStatsVO.TodayStats.builder()
                        .adds(todayAdds)
                        .consumes(todayConsumes)
                        .newExpiring(todayExpiring)
                        .build())
                .expiredItems(expiredItems)
                .todayItems(todayItems)
                .expiringSoonItems(expiringSoonItems)
                .trend(trend)
                .categoryDistribution(categoryDistribution)
                .weeklyTopConsumed(weeklyTop)
                .build();
    }

    /** 家庭展示缓存失效（看板统计 + AI 菜谱推荐）；库存/设备变化由各写入口统一调用 */
    public void evictFamilyCaches(Long familyId) {
        if (familyId == null) {
            return;
        }
        redisTemplate.delete(List.of(STATS_CACHE_PREFIX + familyId,
                AiServiceImpl.RECIPE_CACHE_PREFIX + familyId,
                AiServiceImpl.RECIPE_CACHE_PREFIX + familyId + AiServiceImpl.EXPIRING_CACHE_SUFFIX));
    }

    private Map<Integer, Category> loadCategoryMap(List<Ingredient> ingredients) {
        Set<Integer> ids = ingredients.stream()
                .map(Ingredient::getCategoryId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Integer, Category> map = new HashMap<>();
        for (Category c : categoryMapper.selectBatchIds(ids)) {
            map.put(c.getId(), c);
        }
        return map;
    }

    private IngredientVO toIngredientVO(Ingredient ing, Category cat, LocalDate today,
                                        int remindDays, Map<Long, String> fridgeNameMap) {
        Integer daysUntilExpiry = null;
        boolean expiringSoon = false;
        if (ing.getExpiryDate() != null) {
            daysUntilExpiry = (int) ChronoUnit.DAYS.between(today, ing.getExpiryDate());
            expiringSoon = !ing.getExpiryDate().isAfter(today.plusDays(remindDays));
        }
        return IngredientVO.builder()
                .id(ing.getId())
                .name(ing.getName())
                .categoryName(cat != null ? cat.getName() : "未分类")
                .categoryId(ing.getCategoryId())
                .quantity(ing.getQuantity())
                .unit(ing.getUnit())
                .status(ing.getStatus())
                .expiryDate(ing.getExpiryDate())
                .expiringSoon(expiringSoon)
                .daysUntilExpiry(daysUntilExpiry)
                .fridgeName(fridgeNameMap.getOrDefault(ing.getFridgeId(), ""))
                .build();
    }

    private DashboardStatsVO emptyStats() {
        return DashboardStatsVO.builder()
                .stats(DashboardStatsVO.Stats.builder().build())
                .todayStats(DashboardStatsVO.TodayStats.builder().build())
                .expiredItems(List.of())
                .todayItems(List.of())
                .expiringSoonItems(List.of())
                .trend(List.of())
                .categoryDistribution(List.of())
                .weeklyTopConsumed(List.of())
                .build();
    }
}
