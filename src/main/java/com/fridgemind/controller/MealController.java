package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.MealService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 家庭点餐控制器（一餐制：当前菜单，全员平等增删改查） */
@RestController
@RequestMapping("/api/meal")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;

    /** 点一道菜 */
    @PostMapping
    public Result<?> add(@RequestBody AddRequest req) {
        return Result.success(mealService.add(
                AuthContext.getFamilyId(), AuthContext.getUserId(), req.getRecipeCode(), req.getNote()));
    }

    /** 当前菜单 */
    @GetMapping
    public Result<?> list() {
        return Result.success(mealService.list(AuthContext.getFamilyId()));
    }

    /** 改备注 */
    @PutMapping("/{id}")
    public Result<?> updateNote(@PathVariable Long id, @RequestBody NoteRequest req) {
        mealService.updateNote(AuthContext.getFamilyId(), id, req.getNote());
        return Result.success();
    }

    /** 取消这道菜 */
    @DeleteMapping("/{id}")
    public Result<?> remove(@PathVariable Long id) {
        mealService.remove(AuthContext.getFamilyId(), id);
        return Result.success();
    }

    /** 这顿吃完啦：归档当前菜单（历史保留可查） */
    @DeleteMapping
    public Result<?> archive() {
        mealService.archive(AuthContext.getFamilyId());
        return Result.success();
    }

    /** 归档历史（按归档时间倒序） */
    @GetMapping("/history")
    public Result<?> history() {
        return Result.success(mealService.history(AuthContext.getFamilyId()));
    }

    /** 聚合菜单缺的食材 → 加入购物清单 */
    @PostMapping("/shopping")
    public Result<?> addMissingToShopping() {
        return Result.success(mealService.addMissingToShopping(
                AuthContext.getFamilyId(), AuthContext.getUserId()));
    }

    @Data
    static class AddRequest {
        private String recipeCode;
        private String note;
    }

    /** 改备注请求（与点餐请求分开，语义清晰） */
    @Data
    static class NoteRequest {
        private String note;
    }
}
