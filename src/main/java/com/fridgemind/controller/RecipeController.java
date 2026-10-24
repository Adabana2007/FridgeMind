package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.RecipeService;
import com.fridgemind.vo.PageResult;
import com.fridgemind.vo.RecipeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 食谱控制器：浏览/筛选/详情/分类/收藏/搜索（只读数据） */
@Validated
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /** 搜索请求：page/size 缺省为 0（0 表示未传，服务层兜底成 1/20），故不在此加页码下限校验 */
    @Data
    static class SearchRequest {
        private String keyword;
        private String category;
        @Max(value = 100, message = "每页最多 100 条")
        private int size;
        // 缺省即 0；若用 Integer 不传就是 null，拆箱直接 NPE
        private int page;
    }

    /** 分页浏览：分类/难度/耗时上限/菜名搜索均可选筛选 */
    @GetMapping
    public Result<PageResult<RecipeVO>> page(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Integer maxMin,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
            @RequestParam(defaultValue = "20") @Max(value = 100, message = "每页最多 100 条") int size) {
        return Result.success(recipeService.page(category, difficulty, maxMin, keyword, page, size));
    }

    /** 混合搜索（点餐弹窗选菜）：菜名 LIKE 优先，命中不足自动语义兜底 */
    @PostMapping("/search")
    public Result<?> search(@Valid @RequestBody SearchRequest req) {
        return Result.success(recipeService.search(
                req.getKeyword() == null ? "" : req.getKeyword(), req.getCategory(),
                AuthContext.getFamilyId(),
                req.getPage() > 0 ? req.getPage() : 1,
                req.getSize() > 0 ? req.getSize() : 20));
    }

    /** 详情（含食材/步骤全文 + 当前家庭库存比对：已有/缺/⚡临期） */
    @GetMapping("/{code}")
    public Result<RecipeVO> detail(@PathVariable String code) {
        return Result.success(recipeService.detailWithStock(code, AuthContext.getFamilyId()));
    }

    /** 各分类名称与数量（分类 Tab） */
    @GetMapping("/categories")
    public Result<?> categories() {
        return Result.success(recipeService.categories());
    }

    /** 收藏这道菜（幂等） */
    @PostMapping("/{code}/favorite")
    public Result<?> favorite(@PathVariable String code) {
        recipeService.favorite(AuthContext.getFamilyId(), AuthContext.getUserId(), code);
        return Result.success();
    }

    /** 取消收藏（幂等） */
    @DeleteMapping("/{code}/favorite")
    public Result<?> unfavorite(@PathVariable String code) {
        recipeService.unfavorite(AuthContext.getFamilyId(), code);
        return Result.success();
    }

    /** 家庭收藏列表（含收藏人昵称；"只看收藏"视图 + 点餐弹窗横滑区） */
    @GetMapping("/favorites")
    public Result<?> favorites() {
        return Result.success(recipeService.favorites(AuthContext.getFamilyId()));
    }
}
