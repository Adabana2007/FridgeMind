package com.fridgemind.service;

import com.fridgemind.vo.*;

import java.util.List;

/**
 * 管理员后台服务
 */
public interface AdminService {

    /** 管理员登录，返回 JWT Token */
    String login(String username, String password);

    AdminDashboardVO dashboard();

    PageResult<UserInfoVO> listUsers(int page, int size, String keyword);

    void toggleUser(Long adminId, Long userId, boolean enabled);

    /** 设备列表；keyword 同时匹配冰箱名称与家庭名称，为空则返回全部 */
    List<DeviceVO> listDevices(String keyword);

    void markDeviceFault(Long adminId, Long fridgeId, boolean isFault);

    AdminSmsStatsVO smsStats();

    PageResult<FeedbackVO> listFeedbacks(int page, int size, Integer status);

    void handleFeedback(Long adminId, Long feedbackId, String reply);

    PageResult<AuditLogVO> listAuditLogs(int page, int size, String action, String level,
                                          String dateFrom, String dateTo);

    List<CategoryVO> listCategories();

    /** 添加系统分类；只允许两级，parentId 为空或 0 表示建一级分类 */
    CategoryVO addCategory(Long adminId, String name, Integer parentId, Integer sortOrder);

    void deleteCategory(Long adminId, Integer categoryId);
}
