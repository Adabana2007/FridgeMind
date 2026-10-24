package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.dto.AddFridgeRequest;
import com.fridgemind.dto.UpdateFridgeRequest;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.FridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 冰箱控制器
 */
@RestController
@RequestMapping("/api/fridge")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;

    /** 添加冰箱 */
    @PostMapping("/add")
    public Result<?> add(@Valid @RequestBody AddFridgeRequest req) {
        return Result.success(fridgeService.add(AuthContext.getFamilyId(), req.getName(), req.getDeviceSn()));
    }

    /** 查询家庭下所有冰箱 */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.success(fridgeService.list(AuthContext.getFamilyId()));
    }

    /** 修改冰箱名称 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody UpdateFridgeRequest req) {
        fridgeService.update(id, AuthContext.getFamilyId(), req.getName());
        return Result.success();
    }

    /** 删除冰箱 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        fridgeService.delete(id, AuthContext.getFamilyId());
        return Result.success();
    }
}
