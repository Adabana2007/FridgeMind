package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.ShoppingList;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShoppingListMapper extends BaseMapper<ShoppingList> {

    /** 查家庭购物清单，未购买(is_purchased=0)优先、最新创建在前 */
    default List<ShoppingList> selectByFamilyIdOrdered(Long familyId) {
        return selectList(new LambdaQueryWrapper<ShoppingList>()
                .eq(ShoppingList::getFamilyId, familyId)
                .orderByAsc(ShoppingList::getIsPurchased)
                .orderByDesc(ShoppingList::getCreatedAt));
    }

    /** 查已购买且勾选时间早于阈值的记录（清理旧数据用） */
    default List<ShoppingList> selectExpiredPurchased(Long familyId, LocalDateTime checkedBefore) {
        return selectList(new LambdaQueryWrapper<ShoppingList>()
                .eq(ShoppingList::getFamilyId, familyId)
                .eq(ShoppingList::getIsPurchased, 1)
                .le(ShoppingList::getCheckedAt, checkedBefore));
    }
}
