package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.InventoryLog;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.security.AuthContext;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.InventoryLogMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.service.InventoryLogService;
import com.fridgemind.vo.InventoryLogVO;
import com.fridgemind.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/** 库存日志服务实现（数量变更记日志） */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryLogServiceImpl implements InventoryLogService {

    private final InventoryLogMapper inventoryLogMapper;
    private final UserMapper userMapper;
    private final IngredientMapper ingredientMapper;
    private final FridgeMapper fridgeMapper;

    @Override
    public PageResult<InventoryLogVO> list(Long ingredientId, Long userId, Integer type,
                                            Long fridgeId, Integer confirmed, int page, int size) {
        // 家庭隔离：只允许查询本家庭冰箱的日志
        Long familyId = AuthContext.getFamilyId();
        if (familyId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<Long> allowedFridgeIds = fridgeMapper.selectByFamilyId(familyId).stream()
                .map(Fridge::getId).toList();
        if (fridgeId != null && !allowedFridgeIds.contains(fridgeId)) {
            throw new BusinessException(ResultCode.FRIDGE_PERMISSION_DENIED);
        }
        Page<InventoryLog> mpPage = new Page<>(page, size);
        Page<InventoryLog> result = inventoryLogMapper.selectPageByFilters(mpPage, ingredientId, userId, type, fridgeId, confirmed, allowedFridgeIds);

        List<InventoryLogVO> records = result.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), page, size);
    }

    /** 实体转 VO，关联用户名和食材名 */
    private InventoryLogVO toVO(InventoryLog log) {
        User user = userMapper.selectById(log.getUserId());
        Ingredient ingredient = ingredientMapper.selectById(log.getIngredientId());

        return InventoryLogVO.builder()
                .id(log.getId())
                .ingredientId(log.getIngredientId())
                .ingredientName(ingredient != null ? ingredient.getName() : null)
                .type(log.getType())
                .source(log.getSource())
                .quantityChange(log.getQuantityChange())
                .quantityAfter(log.getQuantityAfter())
                .confirmed(log.getConfirmed())
                .userName(user != null ? user.getNickname() : null)
                .remark(log.getRemark())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
