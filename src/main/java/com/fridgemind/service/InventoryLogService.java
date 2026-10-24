package com.fridgemind.service;

import com.fridgemind.vo.InventoryLogVO;
import com.fridgemind.vo.PageResult;

/**
 * 库存日志服务
 */
public interface InventoryLogService {

    PageResult<InventoryLogVO> list(Long ingredientId, Long userId, Integer type,
                                    Long fridgeId, Integer confirmed, int page, int size);
}
