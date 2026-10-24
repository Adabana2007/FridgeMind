package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.common.ResultCode;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.ShoppingListService;
import com.fridgemind.vo.ShoppingItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 家庭购物清单控制器（AI 菜谱一键加入、勾选只标记已购买） */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    /** 获取家庭购物清单 */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.success(shoppingListService.list(AuthContext.getFamilyId()));
    }

    /** 添加待购商品 */
    @PostMapping("/add")
    public Result<?> add(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        // quantity 缺省 1，unit 缺省"个"
        BigDecimal quantity = new BigDecimal(String.valueOf(body.getOrDefault("quantity", 1)));
        String unit = (String) body.getOrDefault("unit", "个");
        Integer categoryId = body.get("categoryId") != null
                ? ((Number) body.get("categoryId")).intValue() : null;
        return Result.success(shoppingListService.add(
                AuthContext.getFamilyId(), AuthContext.getUserId(), name, quantity, unit, categoryId));
    }

    /** 批量添加（AI食谱一键加入） */
    @PostMapping("/batch")
    public Result<?> batchAdd(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
        List<ShoppingItemVO> items = new ArrayList<>();
        if (rawItems != null) {
            for (Map<String, Object> item : rawItems) {
                items.add(ShoppingItemVO.builder()
                        .name((String) item.get("name"))
                        .quantity(new BigDecimal(String.valueOf(item.getOrDefault("quantity", 1))))
                        .unit((String) item.getOrDefault("unit", "个"))
                        .categoryId(item.get("categoryId") != null
                                ? ((Number) item.get("categoryId")).intValue() : null)
                        .build());
            }
        }
        return Result.success(shoppingListService.batchAdd(
                AuthContext.getFamilyId(), AuthContext.getUserId(), items));
    }

    /** 批量勾选 → 只标记"已购买"（清单与冰箱解耦，不再自动入库） */
    @PutMapping("/batch-check")
    public Result<?> batchCheck(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.get("ids");
        if (rawIds == null || rawIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR);
        }
        List<Long> ids = rawIds.stream().map(Number::longValue).toList();
        int checked = shoppingListService.batchCheck(ids, AuthContext.getFamilyId(), AuthContext.getUserId());
        return Result.success(Map.of("checked", checked));
    }

    /** 勾选已购买（只标记，不入库；入库请在库存页手动添加） */
    @PutMapping("/{id}/check")
    public Result<?> check(@PathVariable Long id) {
        shoppingListService.check(id, AuthContext.getFamilyId(), AuthContext.getUserId());
        return Result.success();
    }

    /** 取消勾选 → 回到待购 */
    @PutMapping("/{id}/uncheck")
    public Result<?> uncheck(@PathVariable Long id) {
        shoppingListService.uncheck(id, AuthContext.getFamilyId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        shoppingListService.delete(id, AuthContext.getFamilyId());
        return Result.success();
    }

    /** 清理超过24小时的已购项 */
    @DeleteMapping("/clean")
    public Result<?> clean() {
        int count = shoppingListService.cleanPurchased(AuthContext.getFamilyId());
        return Result.success(Map.of("cleaned", count));
    }

    /** 清空清单全部项（含待购），App「清空」按钮用 */
    @DeleteMapping("/all")
    public Result<?> clearAll() {
        int count = shoppingListService.clearAll(AuthContext.getFamilyId());
        return Result.success(Map.of("cleared", count));
    }
}
