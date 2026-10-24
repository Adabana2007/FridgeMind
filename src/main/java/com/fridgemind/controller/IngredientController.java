package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.common.ResultCode;
import com.fridgemind.common.BusinessException;
import com.fridgemind.dto.AddIngredientRequest;
import com.fridgemind.dto.ConsumeIngredientRequest;
import com.fridgemind.dto.CorrectIngredientRequest;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.IngredientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 食材控制器
 */
@Validated
@RestController
@RequestMapping("/api/ingredient")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    /** 手动入库添加食材 */
    @PostMapping
    public Result<?> add(@Valid @RequestBody AddIngredientRequest req) {
        // 日期字符串为空则视为未设置（null）
        LocalDate expiryDate = req.getExpiryDate() != null && !req.getExpiryDate().isBlank()
                ? LocalDate.parse(req.getExpiryDate()) : null;
        LocalDate productionDate = req.getProductionDate() != null && !req.getProductionDate().isBlank()
                ? LocalDate.parse(req.getProductionDate()) : null;
        return Result.success(ingredientService.add(
                req.getFridgeId(), req.getName(), req.getCategoryId(),
                req.getQuantity(), req.getUnit(), AuthContext.getUserId(),
                expiryDate, productionDate, req.getRemark()));
    }

    /** 批量添加食材（买菜一次录多件：一行一个名称，公共字段统设） */
    @PostMapping("/batch")
    public Result<?> batchAdd(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) body.get("names");
        // 冰箱与分类是整批共用的必填项，缺失直接拦截（避免后续拆箱 NPE）
        if (names == null || names.isEmpty() || body.get("fridgeId") == null || body.get("categoryId") == null) {
            return Result.error(ResultCode.PARAM_ERROR);
        }
        Long fridgeId = ((Number) body.get("fridgeId")).longValue();
        // 分类缺失必须挡在这里：null 分类会落库成 category_id=NULL 的脏行，让列表页按分类名展示时崩掉
        if (body.get("categoryId") == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类不能为空");
        }
        Integer categoryId = ((Number) body.get("categoryId")).intValue();
        BigDecimal quantity = new BigDecimal(String.valueOf(body.getOrDefault("quantity", 1)));
        String unit = (String) body.getOrDefault("unit", "个");
        Integer expiryDays = body.get("expiryDays") != null ? ((Number) body.get("expiryDays")).intValue() : null;
        String remark = (String) body.getOrDefault("remark", "");
        int count = ingredientService.batchAdd(fridgeId, names, categoryId,
                quantity, unit, AuthContext.getUserId(), expiryDays, remark);
        return Result.success(Map.of("added", count));
    }

    /** 查询食材列表（支持多条件筛选） */
    @GetMapping("/list")
    public Result<?> list(@RequestParam Long fridgeId,
                          @RequestParam(required = false) Integer categoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Integer status,
                          @RequestParam(required = false) Integer expiringWithinDays) {
        // status：1=未开封 2=已开封 3=已过期 4=已用完；不传则不限
        return Result.success(ingredientService.list(fridgeId, categoryId, keyword, status, expiringWithinDays));
    }

    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return Result.success(ingredientService.getById(id));
    }

    /** 修正AI识别结果 */
    @PutMapping("/{id}/correct")
    public Result<?> correct(@PathVariable Long id, @Valid @RequestBody CorrectIngredientRequest req) {
        ingredientService.correct(id, AuthContext.getUserId(), req.getName(), req.getCategoryId(), req.getRemark());
        return Result.success();
    }

    /** 单条消耗食材 */
    @PutMapping("/{id}/consume")
    public Result<?> consume(@PathVariable Long id, @Valid @RequestBody ConsumeIngredientRequest req) {
        ingredientService.consume(id, req.getQuantity(), req.getConfirmed(), AuthContext.getUserId());
        return Result.success();
    }

    /** 批量删除食材 */
    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody Map<String, Object> body) {
        List<Long> ids = extractIds(body);
        ingredientService.batchDelete(ids, AuthContext.getUserId());
        return Result.success();
    }

    /** 批量标记过期 */
    @PutMapping("/batch-expire")
    public Result<?> batchExpire(@RequestBody Map<String, Object> body) {
        List<Long> ids = extractIds(body);
        ingredientService.batchExpire(ids, extractConfirmed(body), AuthContext.getUserId());
        return Result.success();
    }

    /** 批量标记已用完 */
    @PutMapping("/batch-consume")
    public Result<?> batchConsume(@RequestBody Map<String, Object> body) {
        List<Long> ids = extractIds(body);
        ingredientService.batchConsume(ids, extractConfirmed(body), AuthContext.getUserId());
        return Result.success();
    }

    /** 批量修改分类 */
    @PutMapping("/batch-category")
    public Result<?> batchCategory(@RequestBody Map<String, Object> body) {
        List<Long> ids = extractIds(body);
        Object catObj = body.get("categoryId");
        if (catObj == null) {
            return Result.error(com.fridgemind.common.ResultCode.PARAM_ERROR);
        }
        Integer categoryId = ((Number) catObj).intValue();
        ingredientService.batchChangeCategory(ids, categoryId, AuthContext.getUserId());
        return Result.success();
    }

    /** 安全地从请求体中提取 ID 列表，兼容 Integer/Long/String 类型 */
    private List<Long> extractIds(Map<String, Object> body) {
        Object raw = body.get("ids");
        if (raw == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "ids 参数不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) raw;
        return rawIds.stream()
                .map(id -> id instanceof Number ? ((Number) id).longValue() : Long.parseLong(id.toString()))
                .collect(Collectors.toList());
    }

    /** 可选的处理方式：1=已食用 2=已丢弃 3=已过期；不传返回 null（只记数量不记处理方式） */
    private Integer extractConfirmed(Map<String, Object> body) {
        Object raw = body.get("confirmed");
        if (raw == null) {
            return null;
        }
        int value;
        try {
            value = ((Number) raw).intValue();
        } catch (ClassCastException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "confirmed 取值不合法");
        }
        if (value < 1 || value > 3) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "confirmed 取值不合法");
        }
        return value;
    }
}
