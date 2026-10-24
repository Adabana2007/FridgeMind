package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 看板控制器
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** 获取看板统计数据（fridgeId 可选，不传则汇总全部冰箱） */
    @GetMapping("/stats")
    public Result<?> stats(@RequestParam(required = false) Long fridgeId) {
        return Result.success(dashboardService.stats(AuthContext.getFamilyId(), fridgeId));
    }
}
