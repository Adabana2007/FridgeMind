package com.fridgemind.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.InventoryLog;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.InventoryLogMapper;
import com.fridgemind.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 过期巡检定时任务：自动把已过保质期的食材置为"过期"并写一条已确认日志。
 * 幂等：只处理未开封(1)/已开封(2)，已过期(3)/已用完(4)跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirySweepTask {

    private final IngredientMapper ingredientMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final FridgeMapper fridgeMapper;
    private final DashboardService dashboardService;

    /** 启动 15 秒后首扫，之后每小时一扫 */
    @Transactional
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 15_000)
    public void sweepExpiredIngredients() {
        // 幂等：只扫未开封(1)/已开封(2) 且已过保质期；已过期(3)/已用完(4) 本轮不碰
        List<Ingredient> expired = ingredientMapper.selectList(Wrappers.<Ingredient>lambdaQuery()
                .in(Ingredient::getStatus, 1, 2)
                .lt(Ingredient::getExpiryDate, LocalDate.now()));

        for (Ingredient ing : expired) {
            ing.setStatus(3); // 3=已过期
            ingredientMapper.updateById(ing);

            InventoryLog logRow = new InventoryLog();
            logRow.setIngredientId(ing.getId());
            logRow.setFridgeId(ing.getFridgeId());
            // type=3 过期日志，source=2 系统自动，confirmed=3 已确认
            logRow.setType(3);
            logRow.setSource(2);
            logRow.setQuantityChange(BigDecimal.ZERO);
            logRow.setQuantityAfter(ing.getQuantity() != null ? ing.getQuantity() : BigDecimal.ZERO);
            logRow.setConfirmed(3);
            logRow.setRemark("系统巡检自动标记过期");
            inventoryLogMapper.insert(logRow);
        }

        if (!expired.isEmpty()) {
            log.info("[过期巡检] 本轮标记 {} 个食材过期", expired.size());
            // 失效受影响家庭的展示缓存（过期数/临期数会变化）
            expired.stream().map(Ingredient::getFridgeId).distinct()
                    .map(fridgeMapper::selectById).filter(Objects::nonNull)
                    .map(Fridge::getFamilyId).distinct()
                    .forEach(dashboardService::evictFamilyCaches);
        }
    }
}
