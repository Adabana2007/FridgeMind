package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.ShoppingList;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.ShoppingListMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.service.ShoppingListService;
import com.fridgemind.vo.ShoppingItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 家庭购物清单服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShoppingListServiceImpl implements ShoppingListService {

    private final ShoppingListMapper shoppingListMapper;
    private final UserMapper userMapper;

    @Override
    public List<ShoppingItemVO> list(Long familyId) {
        List<ShoppingList> items = shoppingListMapper.selectByFamilyIdOrdered(familyId);

        // 汇总清单涉及的全部用户（添加人+勾选人），一次性查昵称
        List<Long> userIds = items.stream().map(i -> {
            List<Long> ids = new java.util.ArrayList<>();
            ids.add(i.getAddedBy());
            if (i.getCheckedBy() != null) ids.add(i.getCheckedBy());
            return ids;
        }).flatMap(List::stream).distinct().collect(Collectors.toList());

        Map<Long, String> nameMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : ""));

        return items.stream().map(i -> ShoppingItemVO.builder()
                .id(i.getId())
                .familyId(i.getFamilyId())
                .name(i.getName())
                .quantity(i.getQuantity())
                .unit(i.getUnit())
                .categoryId(i.getCategoryId())
                .isPurchased(i.getIsPurchased())
                .addedByName(nameMap.getOrDefault(i.getAddedBy(), ""))
                .checkedByName(i.getCheckedBy() != null ? nameMap.getOrDefault(i.getCheckedBy(), "") : null)
                .checkedAt(i.getCheckedAt())
                .createdAt(i.getCreatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ShoppingItemVO add(Long familyId, Long userId, String name,
                               BigDecimal quantity, String unit, Integer categoryId) {
        ShoppingList item = new ShoppingList();
        item.setFamilyId(familyId);
        item.setName(name);
        item.setQuantity(quantity);
        item.setUnit(unit);
        item.setCategoryId(categoryId);
        item.setIsPurchased(0);
        item.setAddedBy(userId);
        item.setCreatedAt(LocalDateTime.now());
        shoppingListMapper.insert(item);
        log.info("添加购物清单项 id={}, name={}, familyId={}", item.getId(), name, familyId);

        User user = userMapper.selectById(userId);
        return ShoppingItemVO.builder()
                .id(item.getId()).familyId(item.getFamilyId())
                .name(item.getName()).quantity(item.getQuantity()).unit(item.getUnit())
                .categoryId(item.getCategoryId()).isPurchased(item.getIsPurchased())
                .addedByName(user != null ? user.getNickname() : "")
                .createdAt(item.getCreatedAt()).build();
    }

    @Override
    @Transactional
    public List<ShoppingItemVO> batchAdd(Long familyId, Long userId, List<ShoppingItemVO> items) {
        return items.stream().map(item ->
                add(familyId, userId, item.getName(), item.getQuantity(), item.getUnit(), item.getCategoryId())
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int batchCheck(List<Long> ids, Long familyId, Long userId) {
        int count = 0;
        for (Long id : ids) {
            check(id, familyId, userId);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void check(Long id, Long familyId, Long userId) {
        ShoppingList item = shoppingListMapper.selectById(id);
        if (item == null || !item.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "清单项不存在");
        }
        if (item.getIsPurchased() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该项已购买");
        }

        // 只标记为已购买，记录勾选人与勾选时间。
        // 清单与冰箱已解耦：勾选不再自动入库，需要入库请到库存页手动添加。
        item.setIsPurchased(1);
        item.setCheckedBy(userId);
        item.setCheckedAt(LocalDateTime.now());
        shoppingListMapper.updateById(item);

        log.info("购物清单勾选已购买 id={}, checkedBy={}", id, userId);
    }

    @Override
    @Transactional
    public void uncheck(Long id, Long familyId) {
        ShoppingList item = shoppingListMapper.selectById(id);
        if (item == null || !item.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "清单项不存在");
        }
        // 撤销勾选：恢复未购买并清空勾选人/时间
        item.setIsPurchased(0);
        item.setCheckedBy(null);
        item.setCheckedAt(null);
        shoppingListMapper.updateById(item);
    }

    @Override
    @Transactional
    public void delete(Long id, Long familyId) {
        ShoppingList item = shoppingListMapper.selectById(id);
        if (item == null || !item.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "清单项不存在");
        }
        shoppingListMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int cleanPurchased(Long familyId) {
        // 不限勾选时间：勾选即代表"买回来了"，用户点清空就该清掉。
        // 原先只清 checked_at 超 24 小时的，导致刚勾选的完全清不动（点了像没反应），
        // 且 checked_at 为空的历史数据永远清不掉。
        List<ShoppingList> purchased = shoppingListMapper.selectList(
                new LambdaQueryWrapper<ShoppingList>()
                        .eq(ShoppingList::getFamilyId, familyId)
                        .eq(ShoppingList::getIsPurchased, 1));
        for (ShoppingList item : purchased) {
            shoppingListMapper.deleteById(item.getId());
        }
        log.info("清空已购项 familyId={}, 删除 {} 项", familyId, purchased.size());
        return purchased.size();
    }

    @Override
    @Transactional
    public int clearAll(Long familyId) {
        // 清空清单全部项（含待购），App「清空」按钮用
        List<ShoppingList> items = shoppingListMapper.selectByFamilyIdOrdered(familyId);
        for (ShoppingList item : items) {
            shoppingListMapper.deleteById(item.getId());
        }
        log.info("清空购物清单 familyId={}, 删除 {} 项", familyId, items.size());
        return items.size();
    }
}
