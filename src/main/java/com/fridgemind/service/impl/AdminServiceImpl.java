package com.fridgemind.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.*;
import com.fridgemind.mapper.*;
import com.fridgemind.security.JwtUtil;
import com.fridgemind.service.AdminService;
import com.fridgemind.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员后台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper adminMapper;
    private final UserMapper userMapper;
    private final FamilyMapper familyMapper;
    private final FridgeMapper fridgeMapper;
    private final FeedbackMapper feedbackMapper;
    private final AuditLogMapper auditLogMapper;
    private final CategoryMapper categoryMapper;
    private final IngredientMapper ingredientMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.fridgemind.common.AuditLogHelper auditLogHelper;

    @Override
    public String login(String username, String password) {
        // 校验管理员账号：用户名 + BCrypt 密码，通过则签发管理员 token
        Admin admin = adminMapper.selectByUsername(username);
        if (admin == null || !BCrypt.checkpw(password, admin.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_WRONG, "用户名或密码错误");
        }
        return jwtUtil.generateAdminToken(admin.getId());
    }

    @Override
    public AdminDashboardVO dashboard() {
        // 用户/家庭总数（全表 count）
        long totalUsers = userMapper.selectCount(null);
        long totalFamilies = familyMapper.selectCount(null);

        // 设备总数、在线数（online_status=1）、故障数（is_fault=1）
        long totalDevices = fridgeMapper.countAll();
        long activeDevices = fridgeMapper.countByOnlineStatus(1);
        long faultDevices = fridgeMapper.countByFault(1);

        // 待处理反馈（status=0）
        long pendingFeedbacks = feedbackMapper.countByStatus(0);

        // 今日新增审计日志
        long todayAuditLogs = auditLogMapper.countToday();

        // 今日短信统计（Redis）：SCAN 遍历 sms:daily:* 求和（避免 KEYS 全库阻塞）
        long todaySms = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("sms:daily:*").count(100).build())) {
            while (cursor.hasNext()) {
                Object counter = redisTemplate.opsForValue().get(cursor.next());
                todaySms += counter != null ? Long.parseLong(String.valueOf(counter)) : 0;
            }
        }

        return AdminDashboardVO.builder()
                .totalUsers(totalUsers)
                .totalFamilies(totalFamilies)
                .totalDevices(totalDevices)
                .activeDevices(activeDevices)
                .faultDevices(faultDevices)
                .pendingFeedbacks(pendingFeedbacks)
                .todaySms(todaySms)
                .todayAuditLogs(todayAuditLogs)
                .build();
    }

    @Override
    public PageResult<UserInfoVO> listUsers(int page, int size, String keyword) {
        Page<User> result = userMapper.selectPageByKeyword(new Page<>(page, size), keyword);

        List<UserInfoVO> records = result.getRecords().stream()
                .map(u -> UserInfoVO.builder()
                        .id(u.getId())
                        .phone(u.getPhone())
                        .nickname(u.getNickname())
                        .avatar(u.getAvatar())
                        .hasPassword(u.getHasPassword())
                        .enabled(u.getEnabled())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void toggleUser(Long adminId, Long userId, boolean enabled) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        // enabled=1 启用 / 0 禁用，同步到用户状态位
        user.setEnabled(enabled ? 1 : 0);
        userMapper.updateById(user);
        if (!enabled) {
            // 清掉 Redis 中的 token → 旧会话立即失效；放到事务提交后执行，避免回滚后白踢一次
            evictTokenAfterCommit(userId);
        }
        String action = enabled ? "ADMIN_ENABLE_USER" : "ADMIN_DISABLE_USER";
        auditLogHelper.warn(adminId, null, action, "userId=" + userId);
    }

    /** 提交后删除用户 token；无事务上下文时直接执行（防御式，便于单测直接调用） */
    private void evictTokenAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.delete("token:" + userId);
                }
            });
        } else {
            redisTemplate.delete("token:" + userId);
        }
    }

    @Override
    public List<DeviceVO> listDevices(String keyword) {
        List<Fridge> fridges = fridgeMapper.selectList(null);

        // 家庭信息一次性批量查，避免逐条查（关键词过滤与列表展示都要用）
        List<Long> familyIds = fridges.stream()
                .map(Fridge::getFamilyId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Family> familyById = familyIds.isEmpty() ? Map.of()
                : familyMapper.selectBatchIds(familyIds).stream()
                        .collect(Collectors.toMap(Family::getId, Function.identity()));
        // 群主信息：家庭名普遍重名（种子数据里 9 个家庭都叫"我的家"），
        // 手机号才是唯一的人类可读标识，列表与搜索都必须靠它区分
        List<Long> ownerIds = familyById.values().stream()
                .map(Family::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> ownerById = ownerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ownerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        // 关键词匹配四个字段：用户可能只记得冰箱名、家庭名、群主手机号，或只看到过家庭 id。
        // 纯数字关键词（家庭 id / 手机号）用精确匹配：它们本身就是标识，模糊匹配只会带出噪音
        // ——例如搜 "1" 会命中所有含 1 的手机号，搜 "13" 会命中所有 13x 号段。
        // 名称类字段沿用包含匹配。
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            boolean digitsOnly = kw.chars().allMatch(Character::isDigit);
            fridges = fridges.stream().filter(f -> {
                Family family = familyById.get(f.getFamilyId());
                if (family != null && digitsOnly
                        && String.valueOf(family.getId()).equals(kw)) {
                    return true;
                }
                if (f.getName() != null && f.getName().contains(kw)) {
                    return true;
                }
                if (family == null) {
                    return false;
                }
                if (family.getName() != null && family.getName().contains(kw)) {
                    return true;
                }
                User owner = ownerById.get(family.getOwnerId());
                if (owner == null || owner.getPhone() == null) {
                    return false;
                }
                return digitsOnly ? owner.getPhone().equals(kw) : owner.getPhone().contains(kw);
            }).toList();
        }

        return fridges.stream()
                .map(f -> {
                    Family family = familyById.get(f.getFamilyId());
                    User owner = family != null ? ownerById.get(family.getOwnerId()) : null;
                    return DeviceVO.builder()
                            .id(f.getId())
                            .name(f.getName())
                            .deviceSn(f.getDeviceSn())
                            .familyId(f.getFamilyId())
                            .familyName(family != null ? family.getName() : null)
                            .ownerPhone(owner != null ? owner.getPhone() : null)
                            .ownerNickname(owner != null ? owner.getNickname() : null)
                            .onlineStatus(f.getOnlineStatus())
                            // 心跳优先读 Redis（TTL 90s 实时），无则回退 DB 值
                            .lastHeartbeat(readHeartbeat(f.getDeviceSn(), f.getLastHeartbeat()))
                            .isFault(f.getIsFault())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /** 读 Redis 中的设备心跳时间；键不存在/解析失败时回退 DB 值 */
    private LocalDateTime readHeartbeat(String deviceSn, LocalDateTime fallback) {
        try {
            Object v = redisTemplate.opsForValue().get("fridge:online:" + deviceSn);
            if (v != null) {
                return LocalDateTime.parse(v.toString());
            }
        } catch (Exception e) {
            log.warn("读设备心跳失败，回退 DB 值: {}", e.getMessage());
        }
        return fallback;
    }

    @Override
    @Transactional
    public void markDeviceFault(Long adminId, Long fridgeId, boolean isFault) {
        Fridge fridge = fridgeMapper.selectById(fridgeId);
        if (fridge == null) {
            throw new BusinessException(ResultCode.FRIDGE_NOT_FOUND);
        }
        // isFault=1 标记故障 / 0 恢复为正常
        fridge.setIsFault(isFault ? 1 : 0);
        fridgeMapper.updateById(fridge);
        String action = isFault ? "ADMIN_MARK_FRIDGE_FAULT" : "ADMIN_MARK_FRIDGE_NORMAL";
        auditLogHelper.warn(adminId, null, action, "fridgeId=" + fridgeId);
    }

    @Override
    public AdminSmsStatsVO smsStats() {
        // 统计 Redis 中的短信记录
        long todayCount = 0;
        long monthCount = 0;
        List<AdminSmsStatsVO.SmsAlert> alerts = new ArrayList<>();

        String todayPrefix = "sms:daily:";
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(todayPrefix + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object val = redisTemplate.opsForValue().get(key);
                long count = val != null ? Long.parseLong(String.valueOf(val)) : 0;
                todayCount += count;
                if (count >= 10) { // 日发送超过10条触发预警
                    String phone = key.substring(todayPrefix.length());
                    alerts.add(AdminSmsStatsVO.SmsAlert.builder()
                            .phone(phone)
                            .count(count)
                            .lastTime("今天")
                            .build());
                }
            }
        }

        // 费用估算（阿里云短信约 0.045 元/条）
        double estimatedCost = todayCount * 0.045;

        return AdminSmsStatsVO.builder()
                .todayCount(todayCount)
                .monthCount(monthCount) // 月统计需要额外的 Redis key，暂用 todayCount
                .estimatedCost(Math.round(estimatedCost * 100.0) / 100.0)
                .alerts(alerts)
                .build();
    }

    @Override
    public PageResult<FeedbackVO> listFeedbacks(int page, int size, Integer status) {
        Page<Feedback> result = feedbackMapper.selectPageByStatus(new Page<>(page, size), status);

        List<Long> userIds = result.getRecords().stream()
                .map(Feedback::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = Map.of();
        if (!userIds.isEmpty()) {
            nameMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : ""));
        }

        final Map<Long, String> finalNameMap = nameMap;
        List<FeedbackVO> records = result.getRecords().stream()
                .map(f -> FeedbackVO.builder()
                        .id(f.getId())
                        .userId(f.getUserId())
                        .userName(finalNameMap.getOrDefault(f.getUserId(), ""))
                        .content(f.getContent())
                        .status(f.getStatus())
                        .reply(f.getReply())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void handleFeedback(Long adminId, Long feedbackId, String reply) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "反馈不存在");
        }
        feedback.setStatus(1); // 1=已处理
        feedback.setReply(reply);
        feedbackMapper.updateById(feedback);
        auditLogHelper.info(adminId, null, "ADMIN_HANDLE_FEEDBACK", "feedbackId=" + feedbackId);
    }

    @Override
    public PageResult<AuditLogVO> listAuditLogs(int page, int size, String action, String level,
                                                String dateFrom, String dateTo) {
        // 起始日取当天零点、结束日加一天取次日零点，使区间完整覆盖整个结束日
        LocalDateTime from = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDate.parse(dateFrom, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() : null;
        LocalDateTime to = (dateTo != null && !dateTo.isBlank())
                ? LocalDate.parse(dateTo, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1).atStartOfDay() : null;
        Page<AuditLog> result = auditLogMapper.selectPageByFilters(new Page<>(page, size), action, level, from, to);

        // 关联用户名：同时查 user 表和 admin 表
        List<Long> userIds = result.getRecords().stream()
                .map(AuditLog::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).stream()
                    .filter(u -> u.getNickname() != null)
                    .forEach(u -> nameMap.put(u.getId(), u.getNickname()));
            adminMapper.selectBatchIds(userIds).stream()
                    .filter(a -> a.getNickname() != null)
                    .forEach(a -> nameMap.put(a.getId(), a.getNickname()));
        }

        final Map<Long, String> finalNameMap = nameMap;
        List<AuditLogVO> records = result.getRecords().stream()
                .map(l -> AuditLogVO.builder()
                        .id(l.getId())
                        .userId(l.getUserId())
                        .userName(finalNameMap.getOrDefault(l.getUserId(), ""))
                        .action(l.getAction())
                        .target(l.getTarget())
                        .level(l.getLevel())
                        .result(l.getResult())
                        .ip(l.getIp())
                        .createdAt(l.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageResult<>(records, result.getTotal(), page, size);
    }

    @Override
    public List<CategoryVO> listCategories() {
        List<Category> all = categoryMapper.selectAllOrderBySort();
        return buildCategoryTree(all);
    }

    @Override
    @Transactional
    public CategoryVO addCategory(Long adminId, String name, Integer parentId, Integer sortOrder) {
        // 只允许两级：三级分类在树上无处挂载，会被静默丢弃，所以建之前就拦住
        if (parentId != null && parentId != 0) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
            }
            if (parent.getParentId() != null && parent.getParentId() != 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "最多支持两级分类，不能在子分类下再建子分类");
            }
        }
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId);
        category.setFamilyId(null); // 系统分类，不属于任何家庭
        category.setIsSystem(1);
        category.setSortOrder(sortOrder != null ? sortOrder : 99); // 未指定时默认排最后
        categoryMapper.insert(category);
        auditLogHelper.info(adminId, null, "ADMIN_ADD_CATEGORY", "id=" + category.getId() + " name=" + name);
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .isSystem(category.getIsSystem())
                .sortOrder(category.getSortOrder())
                .children(List.of())
                .build();
    }

    @Override
    @Transactional
    public void deleteCategory(Long adminId, Integer categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND);
        }
        Long count = ingredientMapper.countByCategoryId(categoryId);
        // 删除约束：分类下仍有食材引用时不允许删除
        if (count > 0) {
            throw new BusinessException(ResultCode.CATEGORY_HAS_INGREDIENTS);
        }
        categoryMapper.deleteById(categoryId);
        auditLogHelper.warn(adminId, null, "ADMIN_DELETE_CATEGORY", "id=" + categoryId + " name=" + category.getName());
    }

    /** 建树：parentId 为空视为根节点；递归挂载，避免更深的分支被静默丢弃 */
    private List<CategoryVO> buildCategoryTree(List<Category> categories) {
        Map<Integer, List<Category>> grouped = categories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0));
        return grouped.getOrDefault(0, new ArrayList<>()).stream()
                .map(root -> buildCategoryNode(root, grouped))
                .collect(Collectors.toList());
    }

    private CategoryVO buildCategoryNode(Category node, Map<Integer, List<Category>> grouped) {
        return CategoryVO.builder()
                .id(node.getId())
                .name(node.getName())
                .parentId(node.getParentId())
                .isSystem(node.getIsSystem())
                .sortOrder(node.getSortOrder())
                .children(grouped.getOrDefault(node.getId(), List.of()).stream()
                        .map(child -> buildCategoryNode(child, grouped))
                        .collect(Collectors.toList()))
                .build();
    }
}
