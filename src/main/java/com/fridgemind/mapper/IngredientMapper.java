package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Ingredient;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 食材 Mapper
 */
@Mapper
public interface IngredientMapper extends BaseMapper<Ingredient> {

    /** 按冰箱+分类+关键词+状态+到期天数筛选食材，排除已用完（status=4） */
    default List<Ingredient> selectByFilters(Long fridgeId, Integer categoryId,
                                             String keyword, Integer status,
                                             Integer expiringWithinDays) {
        LambdaQueryWrapper<Ingredient> wrapper = new LambdaQueryWrapper<Ingredient>()
                .eq(Ingredient::getFridgeId, fridgeId)
                .ne(Ingredient::getStatus, 4); // 4=已用完，不出现在列表
        if (categoryId != null) {
            wrapper.eq(Ingredient::getCategoryId, categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Ingredient::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Ingredient::getStatus, status);
        }
        if (expiringWithinDays != null) {
            LocalDate today = LocalDate.now();
            wrapper.isNotNull(Ingredient::getExpiryDate)
                   .ge(Ingredient::getExpiryDate, today)
                   .le(Ingredient::getExpiryDate, today.plusDays(expiringWithinDays));
        }
        wrapper.orderByAsc(Ingredient::getExpiryDate)
               .orderByDesc(Ingredient::getCreatedAt);
        return selectList(wrapper);
    }

    /** 按冰箱集合查食材，可选排除某状态（如排除已用完） */
    default List<Ingredient> selectByFridgeIds(List<Long> fridgeIds, Integer excludeStatus) {
        if (fridgeIds == null || fridgeIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Ingredient> wrapper = new LambdaQueryWrapper<Ingredient>()
                .in(Ingredient::getFridgeId, fridgeIds);
        if (excludeStatus != null) {
            wrapper.ne(Ingredient::getStatus, excludeStatus);
        }
        return selectList(wrapper);
    }

    /** 可用食材（未开封/已开封），供 AI 推荐与对话上下文使用（不喂过期/用完的脏数据） */
    default List<Ingredient> selectAvailableByFridgeIds(List<Long> fridgeIds) {
        if (fridgeIds == null || fridgeIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<Ingredient>()
                .in(Ingredient::getFridgeId, fridgeIds)
                .in(Ingredient::getStatus, 1, 2));
    }

    default Long countByCategoryId(Integer categoryId) {
        return selectCount(new LambdaQueryWrapper<Ingredient>()
                .eq(Ingredient::getCategoryId, categoryId));
    }

    /** 统计某冰箱内非指定状态的食材数（如排除已用完） */
    default Long countByFridgeIdExcludeStatus(Long fridgeId, Integer excludeStatus) {
        return selectCount(new LambdaQueryWrapper<Ingredient>()
                .eq(Ingredient::getFridgeId, fridgeId)
                .ne(Ingredient::getStatus, excludeStatus));
    }

    default Long countByFridgeIdAndStatus(Long fridgeId, Integer status) {
        return selectCount(new LambdaQueryWrapper<Ingredient>()
                .eq(Ingredient::getFridgeId, fridgeId)
                .eq(Ingredient::getStatus, status));
    }
}
