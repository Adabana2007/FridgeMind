package com.fridgemind.service;

import com.fridgemind.vo.ShoppingItemVO;

import java.math.BigDecimal;
import java.util.List;

/** 家庭购物清单服务 */
public interface ShoppingListService {

    List<ShoppingItemVO> list(Long familyId);

    ShoppingItemVO add(Long familyId, Long userId, String name, BigDecimal quantity, String unit, Integer categoryId);

    /** 批量添加（AI 食谱一键加入） */
    List<ShoppingItemVO> batchAdd(Long familyId, Long userId, List<ShoppingItemVO> items);

    /** 勾选已购买（只标记，不入库） */
    void check(Long id, Long familyId, Long userId);

    /** 批量勾选已购买（@Transactional） */
    int batchCheck(List<Long> ids, Long familyId, Long userId);

    /** 取消勾选 → 回到待购 */
    void uncheck(Long id, Long familyId);

    void delete(Long id, Long familyId);

    /** 清空已购项：只删 is_purchased=1 的，待购不动；不限勾选时间（含 checked_at 为空的历史数据） */
    int cleanPurchased(Long familyId);

    /** 清空清单全部项（含待购） */
    int clearAll(Long familyId);
}
