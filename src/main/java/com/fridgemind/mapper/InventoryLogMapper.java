package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.entity.InventoryLog;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存变更日志 Mapper
 */
@Mapper
public interface InventoryLogMapper extends BaseMapper<InventoryLog> {

    /** 按食材/用户/类型/冰箱/确认状态多条件动态分页筛选，时间倒序 */
    default Page<InventoryLog> selectPageByFilters(Page<InventoryLog> page,
            Long ingredientId, Long userId, Integer type, Long fridgeId, Integer confirmed,
            List<Long> allowedFridgeIds) {
        LambdaQueryWrapper<InventoryLog> wrapper = new LambdaQueryWrapper<>();
        if (ingredientId != null) wrapper.eq(InventoryLog::getIngredientId, ingredientId);
        if (userId != null) wrapper.eq(InventoryLog::getUserId, userId);
        if (type != null) wrapper.eq(InventoryLog::getType, type);
        if (fridgeId != null) wrapper.eq(InventoryLog::getFridgeId, fridgeId);
        if (confirmed != null) wrapper.eq(InventoryLog::getConfirmed, confirmed);
        if (allowedFridgeIds != null) wrapper.in(InventoryLog::getFridgeId, allowedFridgeIds);
        wrapper.orderByDesc(InventoryLog::getCreatedAt);
        return selectPage(page, wrapper);
    }

    /** 查某食材最近 N 条库存变更，时间倒序 */
    default List<InventoryLog> selectByIngredientIdOrderByCreatedDesc(Long ingredientId, int limit) {
        return selectList(new LambdaQueryWrapper<InventoryLog>()
                .eq(InventoryLog::getIngredientId, ingredientId)
                .orderByDesc(InventoryLog::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /** 查指定冰箱集合在 since 之后的库存变更（用于端到端同步/展示） */
    default List<InventoryLog> selectRecentByFridgeIds(List<Long> fridgeIds, LocalDateTime since) {
        if (fridgeIds == null || fridgeIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<InventoryLog>()
                .in(InventoryLog::getFridgeId, fridgeIds)
                .ge(InventoryLog::getCreatedAt, since));
    }
}
