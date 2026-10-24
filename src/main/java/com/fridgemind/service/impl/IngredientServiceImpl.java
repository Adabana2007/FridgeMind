package com.fridgemind.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fridgemind.common.AuditLogHelper;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.device.DeviceSyncService;
import com.fridgemind.entity.Category;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.InventoryLog;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.CategoryMapper;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.InventoryLogMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.DashboardService;
import com.fridgemind.service.IngredientService;
import com.fridgemind.vo.IngredientDetailVO;
import com.fridgemind.vo.IngredientVO;
import com.fridgemind.vo.InventoryLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 食材服务实现（按冰箱隔离，手动+AI识别，数量变更记日志） */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientServiceImpl implements IngredientService {

    /** 食材状态：1未开封 2已开封 3过期 4已用完（全项目统一口径） */
    private static final int STATUS_UNOPENED = 1;
    private static final int STATUS_EXPIRED = 3;
    private static final int STATUS_USED_UP = 4;

    private final IngredientMapper ingredientMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final FridgeMapper fridgeMapper;
    private final DeviceSyncService deviceSyncService;
    private final AuditLogHelper auditLogHelper;
    private final DashboardService dashboardService;

    @Override
    @Transactional
    public IngredientDetailVO add(Long fridgeId, String name, Integer categoryId,
                                   BigDecimal quantity, String unit, Long userId,
                                   LocalDate expiryDate, LocalDate productionDate,
                                   String remark) {
        validateFridge(fridgeId);

        // 绑定设备的冰箱：同名食材由摄像头计数管理，手动重复登记会被设备回推覆盖，直接拒绝
        Fridge fridge = fridgeMapper.selectById(fridgeId);
        if (fridge.getDeviceSn() != null && ingredientMapper.selectCount(
                Wrappers.<Ingredient>lambdaQuery()
                        .eq(Ingredient::getFridgeId, fridgeId)
                        .eq(Ingredient::getName, name)
                        .isNotNull(Ingredient::getDeviceItemId)) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "该冰箱已有摄像头管理的同名食材，数量以摄像头识别为准；补货请直接放入或使用远程放入");
        }

        Ingredient ingredient = new Ingredient();
        ingredient.setFridgeId(fridgeId);
        ingredient.setName(name);
        ingredient.setCategoryId(categoryId);
        ingredient.setQuantity(quantity);
        ingredient.setUnit(unit);
        // 自动判断过期：保质期已过 → 标记过期
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            ingredient.setStatus(STATUS_EXPIRED);
        } else {
            ingredient.setStatus(STATUS_UNOPENED);
        }
        ingredient.setRecognitionMode("manual");
        ingredient.setExpiryDate(expiryDate);
        ingredient.setProductionDate(productionDate);
        ingredient.setRemark(remark);
        ingredient.setCreatedAt(LocalDateTime.now());
        ingredientMapper.insert(ingredient);

        // 新增入库：同步写一条仅记录初始数量的库存日志（type=1）
        InventoryLog inventoryLog = new InventoryLog();
        inventoryLog.setIngredientId(ingredient.getId());
        inventoryLog.setFridgeId(fridgeId);
        inventoryLog.setUserId(userId);
        inventoryLog.setType(1); // 新增
        inventoryLog.setSource(1); // 手动录入
        inventoryLog.setQuantityChange(quantity);
        inventoryLog.setQuantityAfter(quantity);
        inventoryLog.setCreatedAt(LocalDateTime.now());
        inventoryLogMapper.insert(inventoryLog);

        log.info("手动添加食材 ingredientId={}, name={}, fridgeId={}", ingredient.getId(), name, fridgeId);

        evictCaches(fridgeId);
        return buildDetailVO(ingredient);
    }

    @Override
    public List<IngredientVO> list(Long fridgeId, Integer categoryId, String keyword,
                                    Integer status, Integer expiringWithinDays) {
        validateFridge(fridgeId);
        List<Ingredient> ingredients = ingredientMapper.selectByFilters(fridgeId, categoryId, keyword, status, expiringWithinDays);

        LocalDate today = LocalDate.now();
        // 分类一次查齐再内存取用：逐行 selectById 是列表页的主要 N+1 来源
        Map<Integer, Category> categoryMap = loadCategoryMap(ingredients);

        return ingredients.stream().map(ingredient -> {
            Category category = categoryMap.get(ingredient.getCategoryId());
            // 未设置提醒天数时默认 7 天前开始提醒临期
            int remindDays = category != null && category.getRemindDays() != null
                    ? category.getRemindDays() : 7;

            // 用分类的 remind_days 判断是否临期
            boolean expiringSoon = ingredient.getExpiryDate() != null
                    && !ingredient.getExpiryDate().isAfter(today.plusDays(remindDays));

            // 计算剩余天数（正=还有X天，0=今天，负=已过期）
            Integer daysUntilExpiry = null;
            if (ingredient.getExpiryDate() != null) {
                daysUntilExpiry = (int) java.time.temporal.ChronoUnit.DAYS
                        .between(today, ingredient.getExpiryDate());
            }

            return IngredientVO.builder()
                    .id(ingredient.getId())
                    .name(ingredient.getName())
                    .categoryName(category != null ? category.getName() : "未分类")
                    .categoryId(ingredient.getCategoryId())
                    .quantity(ingredient.getQuantity())
                    .unit(ingredient.getUnit())
                    .status(ingredient.getStatus())
                    .expiryDate(ingredient.getExpiryDate())
                    .expiringSoon(expiringSoon)
                    .daysUntilExpiry(daysUntilExpiry)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public IngredientDetailVO getById(Long ingredientId) {
        Ingredient ingredient = ingredientMapper.selectById(ingredientId);
        if (ingredient == null) {
            throw new BusinessException(ResultCode.INGREDIENT_NOT_FOUND);
        }
        validateOwnership(ingredient);

        List<InventoryLog> recentLogs = inventoryLogMapper.selectByIngredientIdOrderByCreatedDesc(ingredientId, 3);

        return buildDetailVO(ingredient, recentLogs);
    }

    @Override
    @Transactional
    public void correct(Long ingredientId, Long userId, String name,
                         Integer categoryId, String remark) {
        Ingredient ingredient = ingredientMapper.selectById(ingredientId);
        if (ingredient == null) {
            throw new BusinessException(ResultCode.INGREDIENT_NOT_FOUND);
        }
        validateOwnership(ingredient);
        // 摄像头管理的行：名称以下次识别/上报为准，改名会被设备回推覆盖
        if (ingredient.getDeviceItemId() != null && name != null
                && !name.equals(ingredient.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "该食材由摄像头管理，名称以识别结果为准；如需修改请调整分类或备注");
        }

        ingredient.setName(name);
        ingredient.setCategoryId(categoryId);
        ingredient.setCorrectedBy(userId);
        ingredient.setCorrectedAt(LocalDateTime.now());
        ingredient.setRemark(remark);
        ingredientMapper.updateById(ingredient);

        log.info("修正食材信息 ingredientId={}, name={}, correctedBy={}", ingredientId, name, userId);
    }

    @Override
    @Transactional
    public void consume(Long ingredientId, BigDecimal quantity, Integer confirmed, Long userId) {
        Ingredient ingredient = ingredientMapper.selectById(ingredientId);
        if (ingredient == null) {
            throw new BusinessException(ResultCode.INGREDIENT_NOT_FOUND);
        }
        validateOwnership(ingredient);

        // 摄像头管理的食材：数量类操作自动转为远程取出，云端数量以设备回推对账为准
        if (ingredient.getDeviceItemId() != null) {
            Fridge fridge = fridgeMapper.selectById(ingredient.getFridgeId());
            if (fridge == null || fridge.getDeviceSn() == null
                    || !deviceSyncService.isOnline(fridge.getDeviceSn())) {
                throw new BusinessException(ResultCode.PARAM_ERROR,
                        "「" + ingredient.getName() + "」由摄像头管理且冰箱当前离线，请上线后再操作或直接从冰箱取出");
            }
            // 最多取出当前存量，避免超扣
            BigDecimal removeCount = quantity.min(ingredient.getQuantity());
            // 网络下发推迟到事务提交后：事务内只查设备在线状态（本地注册表），不阻塞数据库连接
            pushRemoteRemoveAfterCommit(fridge.getDeviceSn(), ingredient.getDeviceItemId(),
                    removeCount.intValue());
            InventoryLog logEntry = new InventoryLog();
            logEntry.setIngredientId(ingredient.getId());
            logEntry.setFridgeId(ingredient.getFridgeId());
            logEntry.setUserId(userId);
            logEntry.setType(2);
            logEntry.setSource(1);
            logEntry.setQuantityChange(removeCount.negate());
            logEntry.setQuantityAfter(ingredient.getQuantity().subtract(removeCount));
            logEntry.setConfirmed(confirmed);
            logEntry.setRemark("远程取出（摄像头管理食材）");
            logEntry.setCreatedAt(LocalDateTime.now());
            inventoryLogMapper.insert(logEntry);
            return;
        }

        // 扣除后数量归零即视为已用完
        BigDecimal after = ingredient.getQuantity().subtract(quantity);
        if (after.compareTo(BigDecimal.ZERO) <= 0) {
            after = BigDecimal.ZERO;
            ingredient.setStatus(STATUS_USED_UP);
        }
        ingredient.setQuantity(after);
        ingredientMapper.updateById(ingredient);

        // 写日志：用户直接选了处理方式就带上结论，没选则留空（不再有"待标注"流程）
        InventoryLog logEntry = new InventoryLog();
        logEntry.setIngredientId(ingredient.getId());
        logEntry.setFridgeId(ingredient.getFridgeId());
        logEntry.setUserId(userId);
        logEntry.setType(2);
        logEntry.setSource(1);
        logEntry.setQuantityChange(quantity.negate());
        logEntry.setQuantityAfter(after);
        logEntry.setConfirmed(confirmed);
        logEntry.setCreatedAt(LocalDateTime.now());
        inventoryLogMapper.insert(logEntry);

        log.info("消耗食材 ingredientId={}, quantity={}, after={}, confirmed={}",
                ingredientId, quantity, after, confirmed);
        evictCaches(ingredient.getFridgeId());
    }

    @Override
    @Transactional
    public int batchAdd(Long fridgeId, List<String> names, Integer categoryId,
                        BigDecimal quantity, String unit, Long userId,
                        Integer expiryDays, String remark) {
        // 统一保质期：今天起 N 天到期（买菜场景"蔬菜放 7 天"这类批量设定）
        LocalDate expiryDate = expiryDays != null ? LocalDate.now().plusDays(expiryDays) : null;
        int count = 0;
        for (String raw : names) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue; // 空行跳过，允许用户多打一个换行
            }
            add(fridgeId, name, categoryId, quantity, unit, userId,
                    expiryDate, null, remark);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids, Long userId) {
        for (Long id : ids) {
            Ingredient ingredient = ingredientMapper.selectById(id);
            if (ingredient == null) {
                continue;
            }
            validateOwnership(ingredient);

            if (ingredient.getDeviceItemId() != null) {
                // 摄像头管理的食材：删除=远程取出全部，云端行由设备回推置"已用完"（不物理删除，保历史完整）
                Fridge fridge = fridgeMapper.selectById(ingredient.getFridgeId());
                if (fridge == null || fridge.getDeviceSn() == null
                        || !deviceSyncService.isOnline(fridge.getDeviceSn())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "「" + ingredient.getName() + "」由摄像头管理且冰箱离线，请上线后再删除");
                }
                // 网络下发推迟到事务提交后（同上）
                pushRemoteRemoveAfterCommit(fridge.getDeviceSn(), ingredient.getDeviceItemId(),
                        ingredient.getQuantity().intValue());
                InventoryLog logEntry = new InventoryLog();
                logEntry.setIngredientId(ingredient.getId());
                logEntry.setFridgeId(ingredient.getFridgeId());
                logEntry.setUserId(userId);
                logEntry.setType(2);
                logEntry.setSource(1);
                logEntry.setQuantityChange(ingredient.getQuantity().negate());
                logEntry.setQuantityAfter(BigDecimal.ZERO);
                logEntry.setRemark("批量删除（摄像头管理，转远程取出）");
                logEntry.setCreatedAt(LocalDateTime.now());
                inventoryLogMapper.insert(logEntry);
            } else {
                // 物理删除前把食材名冗余进历史日志，避免删除后日志悬空无名
                prefixLogsWithName(ingredient);
                InventoryLog logEntry = new InventoryLog();
                logEntry.setIngredientId(ingredient.getId());
                logEntry.setFridgeId(ingredient.getFridgeId());
                logEntry.setUserId(userId);
                logEntry.setType(2);
                logEntry.setSource(1);
                logEntry.setQuantityChange(ingredient.getQuantity().negate());
                logEntry.setQuantityAfter(BigDecimal.ZERO);
                logEntry.setRemark("批量删除：" + ingredient.getName());
                logEntry.setCreatedAt(LocalDateTime.now());
                inventoryLogMapper.insert(logEntry);
                ingredientMapper.deleteById(id);
            }
        }
        log.info("批量删除食材 ids={}, userId={}", ids, userId);
        auditLogHelper.warn(userId, null, "BATCH_DELETE_INGREDIENT", "ids=" + ids);
        evictCachesByIngredientIds(ids);
    }

    /** 删除前把食材名前缀写入其全部历史日志 */
    private void prefixLogsWithName(Ingredient ingredient) {
        List<InventoryLog> logs = inventoryLogMapper.selectList(Wrappers.<InventoryLog>lambdaQuery()
                .eq(InventoryLog::getIngredientId, ingredient.getId()));
        for (InventoryLog l : logs) {
            String remark = l.getRemark() == null ? "" : l.getRemark();
            if (remark.startsWith("[已删除食材:")) {
                continue;
            }
            l.setRemark("[已删除食材:" + ingredient.getName() + "] " + remark);
            inventoryLogMapper.updateById(l);
        }
    }

    @Override
    @Transactional
    public void batchExpire(List<Long> ids, Integer confirmed, Long userId) {
        applyBatchConfirm(ids, userId, STATUS_EXPIRED, 3, confirmed, false);
        log.info("批量标记过期 ids={}, userId={}", ids, userId);
        auditLogHelper.warn(userId, null, "BATCH_EXPIRE", "ids=" + ids);
    }

    @Override
    @Transactional
    public void batchConsume(List<Long> ids, Integer confirmed, Long userId) {
        applyBatchConfirm(ids, userId, STATUS_USED_UP, 2, confirmed, true);
        log.info("批量标记用完 ids={}, userId={}", ids, userId);
        auditLogHelper.info(userId, null, "BATCH_CONSUME", "ids=" + ids);
    }

    /**
     * 批量处理方式的公共实现（批量过期 / 批量消耗只有状态与类型不同）。
     * 摄像头管理的食材转远程取出，数量以设备回推为准；confirmed 只在非摄像头分支落日志，
     * 保证"用户在界面上看到了什么处理方式，变动记录里就写什么"。
     */
    private void applyBatchConfirm(List<Long> ids, Long userId, int targetStatus, int logType,
                                   Integer confirmed, boolean handleDeviceItems) {
        for (Long id : ids) {
            Ingredient ingredient = ingredientMapper.selectById(id);
            if (ingredient != null) {
                validateOwnership(ingredient);
            }
            // 已处于目标状态则跳过，避免重复写日志
            if (ingredient == null || ingredient.getStatus() == targetStatus) {
                continue;
            }
            if (handleDeviceItems && ingredient.getDeviceItemId() != null) {
                // 摄像头管理：用完=远程取出全部，数量以设备回推为准
                Fridge fridge = fridgeMapper.selectById(ingredient.getFridgeId());
                if (fridge == null || fridge.getDeviceSn() == null
                        || !deviceSyncService.isOnline(fridge.getDeviceSn())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "「" + ingredient.getName() + "」由摄像头管理且冰箱离线，请上线后再操作");
                }
                // 网络下发推迟到事务提交后（同上）
                pushRemoteRemoveAfterCommit(fridge.getDeviceSn(), ingredient.getDeviceItemId(),
                        ingredient.getQuantity().intValue());
                writeLog(ingredient, userId, logType, ingredient.getQuantity().negate(), BigDecimal.ZERO,
                        "批量用完（摄像头管理，转远程取出）", confirmed);
                continue;
            }
            ingredient.setStatus(targetStatus);
            ingredientMapper.updateById(ingredient);
            writeLog(ingredient, userId, logType,
                    handleDeviceItems ? ingredient.getQuantity().negate() : BigDecimal.ZERO,
                    handleDeviceItems ? BigDecimal.ZERO : ingredient.getQuantity(),
                    remarkFor(logType, confirmed), confirmed);
        }
        evictCachesByIngredientIds(ids);
    }

    /** 日志备注：带了处理方式就写用户看到的那个词，否则沿用旧文案 */
    private String remarkFor(int logType, Integer confirmed) {
        if (confirmed == null) {
            return logType == 3 ? "批量标记过期" : "批量标记用完";
        }
        return switch (confirmed) {
            case 1 -> "批量已食用";
            case 2 -> "批量已丢弃";
            case 3 -> "批量已过期";
            default -> logType == 3 ? "批量标记过期" : "批量标记用完";
        };
    }

    @Override
    @Transactional
    public void batchChangeCategory(List<Long> ids, Integer categoryId, Long userId) {
        for (Long id : ids) {
            Ingredient ingredient = ingredientMapper.selectById(id);
            if (ingredient != null) {
                validateOwnership(ingredient);
                ingredient.setCategoryId(categoryId);
                ingredientMapper.updateById(ingredient);
                writeLog(ingredient, userId, 4, BigDecimal.ZERO, ingredient.getQuantity(),
                        "批量修改分类→" + categoryId);
            }
        }
        log.info("批量修改分类 ids={}, categoryId={}, userId={}", ids, categoryId, userId);
        evictCachesByIngredientIds(ids);
    }

    /** 远程取出指令推迟到事务提交后下发：WebSocket 发送是网络调用，留在事务里会拖长事务、占住连接池 */
    private void pushRemoteRemoveAfterCommit(String deviceSn, Long deviceItemId, int count) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deviceSyncService.pushRemoteRemove(deviceSn, deviceItemId, count);
                }
            });
        } else {
            deviceSyncService.pushRemoteRemove(deviceSn, deviceItemId, count);
        }
    }

    /** 按食材涉及的分类 ID 一次查齐（列表页逐行 selectById 会放大成 N+1） */
    private Map<Integer, Category> loadCategoryMap(List<Ingredient> ingredients) {
        Set<Integer> categoryIds = ingredients.stream()
                .map(Ingredient::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            // 必须用 Collections.emptyMap：Map.of() 的不可变实现会在 get(null) 时抛 NPE，
            // 而食材分类允许为空（历史/设备数据），调用方拿 null 走"未分类"兜底
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));
    }

    /** 食材所在冰箱必须属于当前用户家庭 */
    private void validateOwnership(Ingredient ingredient) {
        Long familyId = AuthContext.getFamilyId();
        Fridge fridge = fridgeMapper.selectById(ingredient.getFridgeId());
        if (familyId == null || fridge == null || !fridge.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.INGREDIENT_PERMISSION_DENIED);
        }
    }

    /** 写操作后失效家庭展示缓存（看板 + AI 推荐） */
    private void evictCaches(Long fridgeId) {
        if (fridgeId == null) {
            return;
        }
        Fridge fridge = fridgeMapper.selectById(fridgeId);
        if (fridge != null) {
            dashboardService.evictFamilyCaches(fridge.getFamilyId());
        }
    }

    /** 批量写操作：按实际处理行逐冰箱失效（可能跨多个冰箱） */
    private void evictCachesByIngredientIds(List<Long> ids) {
        ids.stream().map(ingredientMapper::selectById)
                .filter(Objects::nonNull)
                .map(Ingredient::getFridgeId)
                .distinct()
                .forEach(this::evictCaches);
    }

    /** 冰箱必须存在且属于当前用户家庭 */
    private void validateFridge(Long fridgeId) {
        Long familyId = AuthContext.getFamilyId();
        Fridge fridge = fridgeMapper.selectById(fridgeId);
        if (fridge == null) {
            throw new BusinessException(ResultCode.FRIDGE_NOT_FOUND);
        }
        if (familyId == null || !fridge.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.FRIDGE_PERMISSION_DENIED);
        }
    }

    private void writeLog(Ingredient ingredient, Long userId, int type,
                          BigDecimal change, BigDecimal after, String remark) {
        writeLog(ingredient, userId, type, change, after, remark, null);
    }

    /** 带处理方式的重载：confirmed 1=已食用 2=已丢弃 3=已过期，null=只记数量不记处理方式 */
    private void writeLog(Ingredient ingredient, Long userId, int type,
                          BigDecimal change, BigDecimal after, String remark, Integer confirmed) {
        InventoryLog logEntry = new InventoryLog();
        logEntry.setIngredientId(ingredient.getId());
        logEntry.setFridgeId(ingredient.getFridgeId());
        logEntry.setUserId(userId);
        logEntry.setType(type);
        logEntry.setSource(1);
        logEntry.setQuantityChange(change);
        logEntry.setQuantityAfter(after);
        logEntry.setRemark(remark);
        logEntry.setConfirmed(confirmed);
        logEntry.setCreatedAt(LocalDateTime.now());
        inventoryLogMapper.insert(logEntry);
    }

    private IngredientDetailVO buildDetailVO(Ingredient ingredient) {
        return buildDetailVO(ingredient, List.of());
    }

    private IngredientDetailVO buildDetailVO(Ingredient ingredient, List<InventoryLog> logs) {
        Category category = categoryMapper.selectById(ingredient.getCategoryId());
        User corrector = ingredient.getCorrectedBy() != null
                ? userMapper.selectById(ingredient.getCorrectedBy()) : null;

        List<InventoryLogVO> logVOs = logs.stream().map(log -> {
            User logUser = userMapper.selectById(log.getUserId());
            Ingredient logIngredient = ingredientMapper.selectById(log.getIngredientId());
            return InventoryLogVO.builder()
                    .id(log.getId())
                    .ingredientId(log.getIngredientId())
                    .ingredientName(logIngredient != null ? logIngredient.getName() : null)
                    .type(log.getType())
                    .source(log.getSource())
                    .quantityChange(log.getQuantityChange())
                    .quantityAfter(log.getQuantityAfter())
                    .confirmed(log.getConfirmed())
                    .userName(logUser != null ? logUser.getNickname() : null)
                    .remark(log.getRemark())
                    .createdAt(log.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());

        return IngredientDetailVO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .categoryName(category != null ? category.getName() : "未分类")
                .categoryId(ingredient.getCategoryId())
                .quantity(ingredient.getQuantity())
                .unit(ingredient.getUnit())
                .status(ingredient.getStatus())
                .expiryDate(ingredient.getExpiryDate())
                .productionDate(ingredient.getProductionDate())
                .recognitionMode(ingredient.getRecognitionMode())
                .confidence(ingredient.getConfidence())
                .originalName(ingredient.getOriginalName())
                .remark(ingredient.getRemark())
                .correctedAt(ingredient.getCorrectedAt())
                .correctedByName(corrector != null ? corrector.getNickname() : null)
                .recentLogs(logVOs)
                .build();
    }
}
