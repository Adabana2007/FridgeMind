package com.fridgemind.ai;

import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 冰箱库存上下文拼装：把家庭全部食材（含状态与剩余天数）渲染成提示词片段。
 * 供对话链路注入系统提示词使用（由 {@link InventoryContextAdvisor} 调用）。
 */
@Component
@RequiredArgsConstructor
public class InventoryContextBuilder {

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;

    /** 渲染库存上下文；无冰箱或无食材时返回空串（调用方决定如何提示） */
    public String build(Long familyId) {
        List<Fridge> fridges = fridgeMapper.selectByFamilyId(familyId);
        if (fridges.isEmpty()) {
            return "";
        }

        List<Long> fridgeIds = fridges.stream().map(Fridge::getId).toList();
        List<Ingredient> ingredients = ingredientMapper.selectByFridgeIds(fridgeIds, null);
        if (ingredients.isEmpty()) {
            return "";
        }

        Map<Long, String> fridgeNameMap = fridges.stream()
                .collect(Collectors.toMap(Fridge::getId, Fridge::getName));

        StringBuilder ctx = new StringBuilder("当前用户冰箱里的食材如下：\n");
        for (Ingredient ing : ingredients) {
            ctx.append(String.format("- [%s] %s %.2f%s",
                    fridgeNameMap.getOrDefault(ing.getFridgeId(), "未知冰箱"),
                    ing.getName(), ing.getQuantity(), ing.getUnit()));

            String statusText = switch (ing.getStatus()) {
                case 1 -> "未开封";
                case 2 -> "已开封";
                case 3 -> "已过期";
                case 4 -> "已用完";
                default -> "";
            };
            ctx.append(" | 状态: ").append(statusText);

            if (ing.getExpiryDate() != null) {
                // 必须用 DAYS.between 算总天数：until() 返回 Period，getDays() 只取天数分量，跨月会算错（如 9/15→10/3 应为 18 天而非 3 天）
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ing.getExpiryDate());
                if (daysLeft < 0) {
                    ctx.append(" | 已过期 ").append(-daysLeft).append(" 天");
                } else if (daysLeft == 0) {
                    ctx.append(" | 今天过期 ⚠️");
                } else {
                    ctx.append(" | 剩余 ").append(daysLeft).append(" 天");
                }
            }
            ctx.append("\n");
        }

        ctx.append("\n请根据以上食材信息回答用户的问题。优先推荐用户已有的食材搭配，提示即将过期的食材应尽快食用。");
        return ctx.toString();
    }
}
