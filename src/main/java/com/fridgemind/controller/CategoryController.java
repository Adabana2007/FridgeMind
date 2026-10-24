package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 食材分类控制器（只读：分类由系统预置，用户端不再支持增删）
 */
@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 获取家庭分类树（系统预置 + 家庭自定义） */
    @GetMapping("/tree")
    public Result<?> getTree() {
        return Result.success(categoryService.getTree(AuthContext.getFamilyId()));
    }
}
