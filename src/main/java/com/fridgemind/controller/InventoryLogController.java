package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.service.InventoryLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 库存日志控制器（只读查询；原"确认库存变更"接口已随"待标注"流程一并移除）
 */
@Validated
@RestController
@RequestMapping("/api/inventory-log")
@RequiredArgsConstructor
public class InventoryLogController {

    private final InventoryLogService inventoryLogService;

    /** 分页查询库存日志（type：1=存入 2=取出；confirmed：1=已食用 2=已丢弃 3=已过期，空=未记录处理方式） */
    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false) Long ingredientId,
                          @RequestParam(required = false) Long userId,
                          @RequestParam(required = false) Integer type,
                          @RequestParam(required = false) Long fridgeId,
                          @RequestParam(required = false) Integer confirmed,
                          @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
                          @RequestParam(defaultValue = "10") @Max(value = 100, message = "每页最多 100 条") int size) {
        return Result.success(inventoryLogService.list(ingredientId, userId, type, fridgeId, confirmed, page, size));
    }
}
