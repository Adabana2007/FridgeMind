package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.MealPlan;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.MealPlanMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.service.MealService;
import com.fridgemind.service.RecipeService;
import com.fridgemind.service.ShoppingListService;
import com.fridgemind.vo.MealVO;
import com.fridgemind.vo.RecipeVO;
import com.fridgemind.vo.ShoppingItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 家庭点餐服务实现（一餐制：无日期无状态，吃完归档保留历史） */
@Service
@RequiredArgsConstructor
public class MealServiceImpl implements MealService {

    private final MealPlanMapper mealPlanMapper;
    private final UserMapper userMapper;
    private final RecipeService recipeService;
    private final ShoppingListService shoppingListService;

    /** 归档状态：0=当前菜单 1=已归档 */
    private static final int ARCHIVED_NO = 0;
    private static final int ARCHIVED_YES = 1;

    /** 食材用量文本：数字 + 单位（"70g"/"1朵"；"适量"这类无数字走整份） */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)?)(.*)$");

    @Override
    @Transactional
    public MealVO add(Long familyId, Long userId, String recipeCode, String note) {
        recipeService.detail(recipeCode); // 校验食谱存在：空/伪造的 code 直接拒绝，不留脏菜单
        // 只把"当前菜单"算重复：已归档的菜隔天还能再点（归档记录不占用判重）
        Long exists = mealPlanMapper.selectCount(new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getFamilyId, familyId)
                .eq(MealPlan::getRecipeCode, recipeCode)
                .eq(MealPlan::getArchived, ARCHIVED_NO));
        if (exists > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "这道菜已经点过了");
        }
        MealPlan plan = new MealPlan();
        plan.setFamilyId(familyId);
        plan.setRecipeCode(recipeCode);
        plan.setUserId(userId);
        plan.setNote(note != null ? note.trim() : "");
        plan.setCreatedAt(LocalDateTime.now());
        mealPlanMapper.insert(plan);
        return toVOList(List.of(plan)).get(0);
    }

    @Override
    public List<MealVO> list(Long familyId) {
        return toVOList(selectPlans(familyId, ARCHIVED_NO));
    }

    @Override
    public List<MealVO> history(Long familyId) {
        return toVOList(selectPlans(familyId, ARCHIVED_YES));
    }

    @Override
    @Transactional
    public void updateNote(Long familyId, Long id, String note) {
        MealPlan plan = getOwnedPlan(familyId, id);
        plan.setNote(note != null ? note.trim() : "");
        mealPlanMapper.updateById(plan);
    }

    @Override
    @Transactional
    public void remove(Long familyId, Long id) {
        getOwnedPlan(familyId, id);
        mealPlanMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void archive(Long familyId) {
        // 归档而非删除：吃完不清空，保留"谁点了什么"的家庭记忆（历史可查）
        MealPlan update = new MealPlan();
        update.setArchived(1);
        update.setArchivedAt(LocalDateTime.now());
        mealPlanMapper.update(update, new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getFamilyId, familyId)
                .eq(MealPlan::getArchived, 0));
    }

    @Override
    @Transactional
    public int addMissingToShopping(Long familyId, Long userId) {
        List<MealVO> meals = list(familyId);
        if (meals.isEmpty()) {
            return 0;
        }
        // 聚合所有菜缺的食材，同名去重（"盐"在多道菜里缺 → 只加一条）
        Map<String, ShoppingItemVO> merged = new LinkedHashMap<>();
        for (MealVO meal : meals) {
            for (RecipeVO.IngredientItem ing : meal.getMissingIngredients()) {
                merged.putIfAbsent(ing.getName(), toShoppingItem(ing));
            }
        }
        if (merged.isEmpty()) {
            return 0;
        }
        shoppingListService.batchAdd(familyId, userId, new ArrayList<>(merged.values()));
        return merged.size();
    }

    /** 点餐记录 → VO 列表：菜谱一次回查 + 库存一次匹配 + 点餐人一次批量查（避免逐条 N+1） */
    private List<MealVO> toVOList(List<MealPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<String> codes = plans.stream().map(MealPlan::getRecipeCode).distinct().toList();
        List<RecipeVO> recipes = recipeService.listByCodes(codes);
        recipeService.fillStockMatchByFamily(recipes, plans.get(0).getFamilyId()); // 同一家庭，取一条即可
        Map<String, RecipeVO> recipeMap = recipes.stream()
                .collect(Collectors.toMap(RecipeVO::getRecipeCode, Function.identity(), (a, b) -> a));

        List<Long> userIds = plans.stream().map(MealPlan::getUserId).distinct().toList();
        Map<Long, String> userNameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : ""));

        return plans.stream().map(plan -> {
            RecipeVO recipe = recipeMap.get(plan.getRecipeCode());
            return MealVO.builder()
                    .id(plan.getId())
                    .recipeCode(plan.getRecipeCode())
                    .name(recipe != null ? recipe.getName() : plan.getRecipeCode())
                    .imageUrl(recipe != null ? recipe.getImageUrl() : "")
                    .note(plan.getNote())
                    .userName(userNameMap.getOrDefault(plan.getUserId(), ""))
                    .createdAt(plan.getCreatedAt())
                    .archivedAt(plan.getArchivedAt())
                    .missingIngredients(recipe != null && recipe.getMissingIngredients() != null
                            ? recipe.getMissingIngredients() : List.of())
                    .build();
        }).collect(Collectors.toList());
    }

    /** 按归档状态取记录（历史按归档时间倒序，当前菜单按点餐时间正序） */
    private List<MealPlan> selectPlans(Long familyId, int archived) {
        LambdaQueryWrapper<MealPlan> qw = new LambdaQueryWrapper<MealPlan>()
                .eq(MealPlan::getFamilyId, familyId)
                .eq(MealPlan::getArchived, archived);
        if (archived == ARCHIVED_YES) {
            qw.orderByDesc(MealPlan::getArchivedAt);
        } else {
            qw.orderByAsc(MealPlan::getCreatedAt);
        }
        return mealPlanMapper.selectList(qw);
    }

    /** 家庭内校验记录存在性（家庭互信：任何成员可改/删任何记录） */
    private MealPlan getOwnedPlan(Long familyId, Long id) {
        MealPlan plan = mealPlanMapper.selectById(id);
        if (plan == null || !plan.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "点餐记录不存在");
        }
        return plan;
    }

    /** 食材 amount（"适量"/"70g"/"1朵"）→ 购物清单项；无数字按一份计 */
    private ShoppingItemVO toShoppingItem(RecipeVO.IngredientItem ing) {
        var m = AMOUNT_PATTERN.matcher(ing.getAmount() == null ? "" : ing.getAmount());
        if (m.matches()) {
            return ShoppingItemVO.builder()
                    .name(ing.getName())
                    .quantity(new BigDecimal(m.group(1)))
                    .unit(m.group(2).isEmpty() ? "份" : m.group(2))
                    .build();
        }
        return ShoppingItemVO.builder()
                .name(ing.getName())
                .quantity(BigDecimal.ONE)
                .unit("份")
                .build();
    }
}
