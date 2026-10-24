package com.fridgemind.service.impl;

import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Fridge;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.service.FridgeService;
import com.fridgemind.vo.FridgeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 冰箱服务实现（多冰箱同属一家庭；删除前必须清空食材，避免留下打不开也删不掉的孤儿食材） */
@Slf4j
@Service
@RequiredArgsConstructor
public class FridgeServiceImpl implements FridgeService {

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;

    @Override
    @Transactional
    public FridgeVO add(Long familyId, String name, String deviceSn) {
        // 设备序列号全局唯一：一台真冰箱只能被一个家庭绑定，否则设备消息会串库
        // 序列号留空 = 纯软件冰箱（无设备绑定）；空白串统一归一为 NULL，否则第二台软件冰箱会撞 uk_device_sn
        String normalizedSn = (deviceSn != null && !deviceSn.isBlank()) ? deviceSn.trim() : null;
        if (normalizedSn != null) {
            Fridge existed = fridgeMapper.selectOne(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Fridge>lambdaQuery()
                            .eq(Fridge::getDeviceSn, normalizedSn).last("LIMIT 1"));
            if (existed != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该设备序列号已被绑定");
            }
        }

        Fridge fridge = new Fridge();
        fridge.setFamilyId(familyId);
        fridge.setName(name);
        fridge.setDeviceSn(normalizedSn);
        fridge.setOnlineStatus(0); // 默认离线
        fridge.setCreatedAt(LocalDateTime.now());
        fridgeMapper.insert(fridge);

        log.info("添加冰箱 familyId={}, fridgeId={}, name={}", familyId, fridge.getId(), name);

        return FridgeVO.builder()
                .id(fridge.getId())
                .name(fridge.getName())
                .deviceSn(fridge.getDeviceSn())
                .onlineStatus(fridge.getOnlineStatus())
                .ingredientCount(0)
                .expiredCount(0)
                .build();
    }

    @Override
    public List<FridgeVO> list(Long familyId) {
        List<Fridge> fridges = fridgeMapper.selectByFamilyId(familyId);

        return fridges.stream().map(fridge -> {
            // 统计食材总数（排除已用完的状态4）
            Long totalCount = ingredientMapper.countByFridgeIdExcludeStatus(fridge.getId(), 4);

            // 统计过期食材数量（状态=3）
            Long expiredCount = ingredientMapper.countByFridgeIdAndStatus(fridge.getId(), 3);

            return FridgeVO.builder()
                    .id(fridge.getId())
                    .name(fridge.getName())
                    .deviceSn(fridge.getDeviceSn())
                    .onlineStatus(fridge.getOnlineStatus())
                    .ingredientCount(totalCount != null ? totalCount.intValue() : 0)
                    .expiredCount(expiredCount != null ? expiredCount.intValue() : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void update(Long fridgeId, Long familyId, String name) {
        Fridge fridge = validateOwnership(fridgeId, familyId);
        fridge.setName(name);
        fridgeMapper.updateById(fridge);

        log.info("修改冰箱名称 fridgeId={}, name={}", fridgeId, name);
    }

    @Override
    @Transactional
    public void delete(Long fridgeId, Long familyId) {
        validateOwnership(fridgeId, familyId);

        // 有未清空的食材时拒绝删除：食材靠 fridge_id 归属家庭，冰箱没了它们既查不到也删不掉
        Long ingredientCount = ingredientMapper.countByFridgeIdExcludeStatus(fridgeId, 4);
        if (ingredientCount != null && ingredientCount > 0) {
            throw new BusinessException(ResultCode.FRIDGE_HAS_INGREDIENTS);
        }

        fridgeMapper.deleteById(fridgeId);

        log.info("删除冰箱 fridgeId={}, familyId={}", fridgeId, familyId);
    }

    /** 校验冰箱归属当前家庭，返回冰箱实体 */
    private Fridge validateOwnership(Long fridgeId, Long familyId) {
        Fridge fridge = fridgeMapper.selectById(fridgeId);
        if (fridge == null) {
            throw new BusinessException(ResultCode.FRIDGE_NOT_FOUND);
        }
        if (!familyId.equals(fridge.getFamilyId())) {
            throw new BusinessException(ResultCode.FRIDGE_PERMISSION_DENIED);
        }
        return fridge;
    }
}
