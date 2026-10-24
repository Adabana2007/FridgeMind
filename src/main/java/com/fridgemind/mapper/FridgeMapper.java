package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Fridge;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 冰箱 Mapper
 */
@Mapper
public interface FridgeMapper extends BaseMapper<Fridge> {

    default List<Fridge> selectByFamilyId(Long familyId) {
        return selectList(new LambdaQueryWrapper<Fridge>()
                .eq(Fridge::getFamilyId, familyId));
    }

    /** 统计指定在线状态（0=离线, 1=在线）的冰箱数 */
    default Long countByOnlineStatus(Integer onlineStatus) {
        return selectCount(new LambdaQueryWrapper<Fridge>()
                .eq(Fridge::getOnlineStatus, onlineStatus));
    }

    /** 统计故障（is_fault=1）的冰箱数 */
    default Long countByFault(Integer isFault) {
        return selectCount(new LambdaQueryWrapper<Fridge>()
                .eq(Fridge::getIsFault, isFault));
    }

    default Long countAll() {
        return selectCount(null);
    }
}
