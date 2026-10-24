package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.dto.AdminLoginRequest;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员后台控制器
 * 所有接口需管理员 Token（AdminAuthInterceptor 拦截）。
 */
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** 管理员登录（拦截器已排除此接口） */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody AdminLoginRequest req) {
        String token = adminService.login(req.getUsername(), req.getPassword());
        return Result.success(Map.of("token", token));
    }

    /** 仪表盘统计 */
    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        return Result.success(adminService.dashboard());
    }

    /** 用户列表 */
    @GetMapping("/users")
    public Result<?> users(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
                           @RequestParam(defaultValue = "20") @Max(value = 100, message = "每页最多 100 条") int size,
                           @RequestParam(required = false) String keyword) {
        return Result.success(adminService.listUsers(page, size, keyword));
    }

    /** 禁用/启用用户 */
    @PutMapping("/users/{id}/toggle")
    public Result<?> toggleUser(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        // enabled 缺省 true，即默认启用用户
        adminService.toggleUser(AuthContext.getUserId(), id, body.getOrDefault("enabled", true));
        return Result.success();
    }

    /** 设备监控列表；keyword 同时匹配冰箱名称与家庭名称 */
    @GetMapping("/devices")
    public Result<?> devices(@RequestParam(required = false) String keyword) {
        return Result.success(adminService.listDevices(keyword));
    }

    /** 标记设备故障/正常 */
    @PutMapping("/devices/{id}/fault")
    public Result<?> markFault(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        // isFault 缺省 false，即默认标记为正常
        adminService.markDeviceFault(AuthContext.getUserId(), id, body.getOrDefault("isFault", false));
        return Result.success();
    }

    /** 短信统计 */
    @GetMapping("/sms-stats")
    public Result<?> smsStats() {
        return Result.success(adminService.smsStats());
    }

    /** 反馈列表 */
    @GetMapping("/feedbacks")
    public Result<?> feedbacks(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
                               @RequestParam(defaultValue = "20") @Max(value = 100, message = "每页最多 100 条") int size,
                               @RequestParam(required = false) Integer status) {
        return Result.success(adminService.listFeedbacks(page, size, status));
    }

    /** 处理反馈 */
    @PutMapping("/feedbacks/{id}/handle")
    public Result<?> handleFeedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminService.handleFeedback(AuthContext.getUserId(), id, body.get("reply"));
        return Result.success();
    }

    /** 审计日志 */
    @GetMapping("/audit-logs")
    public Result<?> auditLogs(@RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
                               @RequestParam(defaultValue = "20") @Max(value = 100, message = "每页最多 100 条") int size,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) String level,
                               @RequestParam(required = false) String dateFrom,
                               @RequestParam(required = false) String dateTo) {
        return Result.success(adminService.listAuditLogs(page, size, action, level, dateFrom, dateTo));
    }

    /** 获取全部分类树 */
    @GetMapping("/categories")
    public Result<?> categories() {
        return Result.success(adminService.listCategories());
    }

    /** 添加系统分类 */
    @PostMapping("/categories")
    public Result<?> addCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).intValue() : null;
        Integer sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : null;
        return Result.success(adminService.addCategory(AuthContext.getUserId(), name, parentId, sortOrder));
    }

    /** 删除分类 */
    @DeleteMapping("/categories/{id}")
    public Result<?> deleteCategory(@PathVariable Integer id) {
        adminService.deleteCategory(AuthContext.getUserId(), id);
        return Result.success();
    }
}
