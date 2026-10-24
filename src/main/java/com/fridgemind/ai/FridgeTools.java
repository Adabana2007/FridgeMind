package com.fridgemind.ai;

import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.RecipeService;
import com.fridgemind.vo.RecipeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 对话可调用的冰箱工具（只读）。
 * 家庭上下文取自 {@link AuthContext}（与 HTTP 请求同线程），模型无法通过参数越权访问其他家庭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FridgeTools {

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;
    private final RecipeService recipeService;

    /** 查询某食材在当前家庭的库存情况（含状态与剩余天数） */
    @Tool(name = "queryIngredientStock", description = "查询用户冰箱里指定食材的库存数量、单位、状态和剩余保质期天数")
    public String queryIngredientStock(
            @ToolParam(description = "食材名称，如 鸡蛋、牛奶") String name) {
        Long familyId = AuthContext.getFamilyId();
        if (familyId == null || !StringUtils.hasText(name)) {
            return "无法查询：缺少家庭上下文或食材名称";
        }

        List<Long> fridgeIds = fridgeMapper.selectByFamilyId(familyId).stream().map(Fridge::getId).toList();
        if (fridgeIds.isEmpty()) {
            return "用户还没有添加冰箱";
        }

        // 名称包含匹配：模型可能只说"鸡蛋"，而库存里记的是"土鸡蛋"
        List<String> lines = new ArrayList<>();
        for (Ingredient ing : ingredientMapper.selectByFridgeIds(fridgeIds, null)) {
            if (ing.getName() != null && ing.getName().contains(name)) {
                lines.add(describe(ing));
            }
        }
        return lines.isEmpty()
                ? "冰箱里没有找到「" + name + "」相关的食材"
                : "「" + name + "」相关库存：\n" + String.join("\n", lines);
    }

    /** 查询某道菜所需食材里，用户已有/缺少哪些 */
    @Tool(name = "queryRecipeIngredients", description = "查询指定菜谱需要哪些食材，以及用户冰箱里已有哪些、还缺哪些")
    public String queryRecipeIngredients(
            @ToolParam(description = "菜谱标识 recipeCode，形如 rc_xxxxxxxxxxxx") String recipeCode) {
        Long familyId = AuthContext.getFamilyId();
        if (!StringUtils.hasText(recipeCode)) {
            return "无法查询：缺少菜谱标识";
        }
        try {
            RecipeVO recipe = recipeService.detailWithStock(recipeCode, familyId);
            return String.format("菜谱「%s」：已有 %s；缺少 %s", recipe.getName(),
                    names(recipe.getHaveIngredients()), names(recipe.getMissingIngredients()));
        } catch (Exception e) {
            log.warn("[AI工具] 查询菜谱食材失败 recipeCode={}: {}", recipeCode, e.getMessage());
            return "没有找到这道菜谱，请确认菜谱标识是否正确";
        }
    }

    private String names(List<RecipeVO.IngredientItem> items) {
        if (items == null || items.isEmpty()) {
            return "无";
        }
        return items.stream().map(RecipeVO.IngredientItem::getName).collect(Collectors.joining("、"));
    }

    /** 食材一行描述：名称 数量单位 | 状态 | 剩余天数 */
    private String describe(Ingredient ing) {
        StringBuilder sb = new StringBuilder(String.format("- %s %.2f%s",
                ing.getName(), ing.getQuantity(), ing.getUnit()));
        sb.append(" | 状态: ").append(switch (ing.getStatus()) {
            case 1 -> "未开封";
            case 2 -> "已开封";
            case 3 -> "已过期";
            case 4 -> "已用完";
            default -> "未知";
        });
        if (ing.getExpiryDate() != null) {
            // 与库存上下文统一口径：DAYS.between 算总天数，不能用 until().getDays()
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ing.getExpiryDate());
            sb.append(daysLeft < 0 ? " | 已过期 " + (-daysLeft) + " 天"
                    : daysLeft == 0 ? " | 今天过期" : " | 剩余 " + daysLeft + " 天");
        }
        return sb.toString();
    }
}
