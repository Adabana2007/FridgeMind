package com.fridgemind.device;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Category;
import com.fridgemind.entity.Fridge;
import com.fridgemind.entity.Ingredient;
import com.fridgemind.entity.InventoryLog;
import com.fridgemind.mapper.CategoryMapper;
import com.fridgemind.mapper.FridgeMapper;
import com.fridgemind.mapper.IngredientMapper;
import com.fridgemind.mapper.InventoryLogMapper;
import com.fridgemind.service.DashboardService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 设备同步服务：设备协议消息 → 数据库操作。
 * 云端库存真相 = 识别事件流 + 手动操作；板端快照仅回填 device_item_id 映射，不影响云端账本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSyncService {

    private final FridgeMapper fridgeMapper;
    private final IngredientMapper ingredientMapper;
    private final InventoryLogMapper inventoryLogMapper;
    private final CategoryMapper categoryMapper;
    private final DeviceSessionRegistry registry;
    private final ClientSessionRegistry clientRegistry;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DashboardService dashboardService;

    /** 设备在线心跳键前缀（TTL 过期即离线，与 90s 判离线语义一致） */
    private static final String ONLINE_PREFIX = "fridge:online:";
    private static final int ONLINE_TTL_SECONDS = 90;

    /** 冰箱在线状态：0离线 1在线 */
    private static final int ONLINE_STATUS_ONLINE = 1;

    /** 食材状态：1未开封 2已开封 3过期 4已用完（全项目统一口径） */
    private static final int STATUS_UNOPENED = 1;
    private static final int STATUS_EXPIRED = 3;
    private static final int STATUS_USED_UP = 4;

    /** 系统预置分类名称 → ID，启动时一次装载，用于把设备上报的中文分类映射到 category_id */
    private final Map<String, Integer> systemCategories = new LinkedHashMap<>();

    /**
     * 摄像头 10 类词表 → 云端分类ID + 默认保质期天数（封闭集合，零猜测）
     * 分类ID：1水果 2蔬菜 3肉类 4蛋类 5乳制品 6饮料 7包装食品 8其他
     */
    private static final Map<String, int[]> DEVICE_FOOD_MAP = Map.of(
            "苹果", new int[]{1, 14},
            "香蕉", new int[]{1, 5},
            "彩椒", new int[]{2, 7},
            "肉类", new int[]{3, 3},
            "鸡蛋", new int[]{4, 30},
            "牛奶", new int[]{5, 7},
            "饮料", new int[]{6, 180},
            "面包", new int[]{7, 5},
            "饼干", new int[]{7, 90},
            "罐头", new int[]{7, 365}
    );

    /** 后端重启时，所有设备的旧连接已随重启消失，统一置离线（设备会在 5 秒内重连并自动恢复在线） */
    @PostConstruct
    public void markAllOfflineAtStartup() {
        fridgeMapper.update(null, Wrappers.<Fridge>lambdaUpdate()
                .set(Fridge::getOnlineStatus, 0)
                .eq(Fridge::getOnlineStatus, ONLINE_STATUS_ONLINE));
    }

    @PreDestroy
    public void shutdown() {
        markAllOfflineAtStartup();
    }

    /** 每 30 秒检查一次：把心跳超时（90 秒）的设备连接关闭，触发离线流程 */
    @Scheduled(fixedDelay = 30_000)
    public void checkHeartbeatTimeout() {
        registry.closeStale();
    }

    /** 白名单（fridge.device_sn）+ 密钥校验；设备未配置密钥时不校验 */
    public boolean canAccept(String deviceSn, String key) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge == null) {
            return false;
        }
        return fridge.getDeviceKey() == null || fridge.getDeviceKey().equals(key);
    }

    /** 设备注册：置在线 + 记录心跳 */
    public void onRegister(String deviceSn, String deviceName) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge == null) {
            return;
        }
        fridgeMapper.update(null, Wrappers.<Fridge>lambdaUpdate()
                .set(Fridge::getOnlineStatus, ONLINE_STATUS_ONLINE)
                .set(Fridge::getLastHeartbeat, LocalDateTime.now())
                .eq(Fridge::getId, fridge.getId()));
        markOnline(deviceSn);
        log.info("[设备] {}({}) 已注册上线", fridge.getName(), deviceSn);
        notifyFamily(fridge, Map.of("type", "device_status", "deviceSn", deviceSn,
                "fridgeName", fridge.getName(), "online", true));
    }

    /** 心跳：只写 Redis（TTL 90s 过期即离线）；DB 心跳仅在注册/断开时写，避免高频写库 */
    public void onHeartbeat(String deviceSn) {
        markOnline(deviceSn);
    }

    /** 连接断开：置离线 */
    public void onDisconnect(String deviceSn) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge != null) {
            fridgeMapper.update(null, Wrappers.<Fridge>lambdaUpdate()
                    .set(Fridge::getOnlineStatus, 0)
                    .eq(Fridge::getId, fridge.getId()));
            redisTemplate.delete(ONLINE_PREFIX + deviceSn);
            log.info("[设备] {}({}) 已离线", fridge.getName(), deviceSn);
            notifyFamily(fridge, Map.of("type", "device_status", "deviceSn", deviceSn,
                    "fridgeName", fridge.getName(), "online", false));
        }
    }

    private void markOnline(String deviceSn) {
        redisTemplate.opsForValue().set(ONLINE_PREFIX + deviceSn,
                LocalDateTime.now().toString(), ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 板端库存快照：只回填 device_item_id 映射（remote_remove 点名用），
     * 差异仅打告警日志，不改云端账本（库存真相 = 事件流 + 手动操作）。
     */
    public void onInventory(String deviceSn, JsonNode items) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge == null || items == null || !items.isArray()) {
            return;
        }

        var rows = ingredientMapper.selectList(Wrappers.<Ingredient>lambdaQuery()
                .eq(Ingredient::getFridgeId, fridge.getId())
                .orderByDesc(Ingredient::getId));

        Map<Long, Ingredient> byDeviceItemId = new HashMap<>();
        Map<String, Ingredient> unclaimedByName = new HashMap<>();
        for (Ingredient row : rows) {
            if (row.getDeviceItemId() != null) {
                byDeviceItemId.put(row.getDeviceItemId(), row);
            } else {
                // 同名无主行取最新一条（旧的无主行为历史遗留，不参与映射）
                unclaimedByName.put(row.getName(), row);
            }
        }

        int mapped = 0;
        for (JsonNode item : items) {
            long deviceItemId = item.path("id").asLong();
            String name = item.path("name").asText("");
            if (deviceItemId <= 0 || name.isEmpty()) {
                continue;
            }

            // 已映射且名称一致：无需处理
            Ingredient mappedIng = byDeviceItemId.get(deviceItemId);
            boolean needReclaim = mappedIng == null || !name.equals(mappedIng.getName());
            if (!needReclaim) {
                continue;
            }

            // 按名称找无主行回填映射；板端条目 ID 复用/换主时先解除旧映射，避免唯一索引冲突
            Ingredient target = unclaimedByName.get(name);
            if (target == null) {
                continue;
            }
            if (mappedIng != null) {
                mappedIng.setDeviceItemId(null);
                ingredientMapper.updateById(mappedIng);
                byDeviceItemId.remove(deviceItemId);
            }
            target.setDeviceItemId(deviceItemId);
            ingredientMapper.updateById(target);
            byDeviceItemId.put(deviceItemId, target);
            unclaimedByName.remove(name);
            mapped++;

            // 库存差异仅提示，不改云端账本
            BigDecimal boardQty = decimal(item.path("quantity"), null);
            if (boardQty != null && target.getQuantity() != null
                    && boardQty.compareTo(target.getQuantity()) != 0) {
                log.warn("[设备] {} 条目 {} '{}' 板端={} 云端账本={}（差异仅提示，云端以事件流为准）",
                        deviceSn, deviceItemId, name, boardQty, target.getQuantity());
            }
        }
        log.info("[设备] {} 库存快照映射更新：{} 项（云端账本不受影响）", deviceSn, mapped);
        notifyFamily(fridge, Map.of("type", "inventory_changed", "fridgeId", fridge.getId()));
        dashboardService.evictFamilyCaches(fridge.getFamilyId());
    }

    /**
     * 识别事件：云端库存的唯一事件驱动源（板端本地库存不作为数据源）。
     * 放入/取出按名称增减（取出立即减库，下限 0；日志的 confirmed 留空，不再有"待确认"环节）；
     * 断线重放按(食材,时间,类型,变化量,事件剩余)幂等去重。
     */
    public void onEvent(String deviceSn, JsonNode event) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge == null || event == null || event.isNull()) {
            return;
        }
        int action = event.path("action").asInt(-1);
        BigDecimal change = decimal(event.path("quantityChange"), BigDecimal.ZERO);
        if (change.compareTo(BigDecimal.ZERO) == 0) {
            return; // 整理/无数量变化的事件不入库
        }
        // 防御：取出类动作（1=取出, 2=部分取, 4=不明消失）数量必须为负
        if ((action == 1 || action == 2 || action == 4) && change.signum() > 0) {
            change = change.negate();
        }

        String name = event.path("itemName").asText("");
        BigDecimal confidence = decimal(event.path("confidence"), null);
        BigDecimal eventAfter = decimal(event.path("afterQuantity"), null);
        LocalDateTime eventTime = parseEventTime(event.path("timestamp"));
        boolean isAdd = change.signum() > 0;

        // 按名称查找（设备行优先 → 手动行取最新），云端账本只认名称
        Ingredient ing = ingredientMapper.selectOne(Wrappers.<Ingredient>lambdaQuery()
                .eq(Ingredient::getFridgeId, fridge.getId())
                .eq(Ingredient::getName, name)
                .isNotNull(Ingredient::getDeviceItemId)
                .last("LIMIT 1"));
        if (ing == null) {
            ing = ingredientMapper.selectOne(Wrappers.<Ingredient>lambdaQuery()
                    .eq(Ingredient::getFridgeId, fridge.getId())
                    .eq(Ingredient::getName, name)
                    .isNull(Ingredient::getDeviceItemId)
                    .orderByDesc(Ingredient::getId)
                    .last("LIMIT 1"));
        }
        if (ing == null) {
            // 云端无此账：放入按板端 after 建账；取出按板端 after 建"完结"账（0 数量不产生负库存）
            BigDecimal startQty = eventAfter != null && eventAfter.signum() > 0
                    ? eventAfter
                    : (isAdd && change.signum() > 0 ? change : BigDecimal.ZERO);
            ing = insertIngredient(fridge.getId(), null, name, startQty, "个", null,
                    confidence, isAdd ? 1 : (startQty.signum() > 0 ? 1 : 4));
            if (ing == null) {
                return; // 分类缺失，账目已跳过（classify 已打警告日志）
            }
        }

        // A3 断线补传去重：板端重连会重放未同步事件，按(食材,时间,类型,变化量,事件剩余)幂等
        if (eventTime != null && inventoryLogMapper.selectCount(Wrappers.<InventoryLog>lambdaQuery()
                .eq(InventoryLog::getIngredientId, ing.getId())
                .eq(InventoryLog::getCreatedAt, eventTime)
                .eq(InventoryLog::getType, isAdd ? 1 : 2)
                .eq(InventoryLog::getQuantityChange, change)
                .eq(InventoryLog::getQuantityAfter,
                        eventAfter != null ? eventAfter : BigDecimal.ZERO)) > 0) {
            log.debug("[设备] {} 重放事件已入账，忽略: {} {} x{} @{}", deviceSn, name, change, eventTime);
            return;
        }

        // v2：事件驱动库存（云端账本为真相，立即增减）
        BigDecimal curQty = ing.getQuantity() == null ? BigDecimal.ZERO : ing.getQuantity();
        if (isAdd) {
            ing.setQuantity(curQty.add(change));
            if (ing.getStatus() != null && ing.getStatus() == STATUS_USED_UP
                    && ing.getQuantity().signum() > 0) {
                ing.setStatus(STATUS_UNOPENED); // 重新放入 → 从"已用完"复活
            }
        } else {
            ing.setQuantity(curQty.add(change).max(BigDecimal.ZERO));
            if (ing.getQuantity().signum() == 0 && ing.getStatus() != null
                    && ing.getStatus() != STATUS_EXPIRED) {
                ing.setStatus(STATUS_USED_UP); // 扣到 0 → 已用完（过期状态不被覆盖）
            }
        }
        if (confidence != null) {
            ing.setConfidence(confidence); // 识别置信度跟随最近一次事件刷新
        }
        ingredientMapper.updateById(ing);

        InventoryLog logRow = new InventoryLog();
        logRow.setIngredientId(ing.getId());
        logRow.setFridgeId(fridge.getId());
        logRow.setType(isAdd ? 1 : 2);
        logRow.setSource(2);
        logRow.setQuantityChange(change);
        logRow.setQuantityAfter(eventAfter != null ? eventAfter : ing.getQuantity());
        // confirmed 留空：只有用户/系统给出明确结论时才写值（"待标注"流程已取消）
        logRow.setRemark("设备自动识别: action=" + action
                + (confidence != null ? ", 置信度=" + confidence : ""));
        logRow.setCreatedAt(eventTime != null ? eventTime : LocalDateTime.now());
        inventoryLogMapper.insert(logRow);
        log.info("[设备] {} 识别事件: {} {} x{} (云端账本={})", deviceSn,
                isAdd ? "放入" : "取出", name, change, ing.getQuantity());
        notifyFamily(fridge, Map.of(
                "type", "new_event",
                "fridgeId", fridge.getId(),
                "itemName", name,
                "isAdd", isAdd,
                "quantityChange", change));
        dashboardService.evictFamilyCaches(fridge.getFamilyId());
    }

    /**
     * 远程添加：小程序/Web → 设备。
     * v2：下发即下发，云端不伪造"远程放入"日志（库存增减由事件流 / 手动入口落账；
     * 设备执行后回推的 inventory_update 只更新 id 映射，不改云端账本）。
     */
    public boolean pushRemoteAdd(String deviceSn, String name, int count, String category) {
        String payload = "{\"type\":\"remote_add\",\"name\":\"" + escape(name)
                + "\",\"count\":" + count
                + ",\"category\":\"" + escape(orDefault(category, "蔬菜水果")) + "\"}";
        boolean ok = registry.send(deviceSn, new TextMessage(payload));
        log.info("[设备] 下发 remote_add {} x{} → {} ({})", name, count, deviceSn, ok ? "成功" : "设备离线");
        return ok;
    }

    /** 远程减少：itemId 为设备端本地条目ID */
    public boolean pushRemoteRemove(String deviceSn, long itemId, int count) {
        String payload = "{\"type\":\"remote_remove\",\"itemId\":" + itemId
                + ",\"count\":" + count + "}";
        boolean ok = registry.send(deviceSn, new TextMessage(payload));
        log.info("[设备] 下发 remote_remove itemId={} x{} → {} ({})", itemId, count, deviceSn, ok ? "成功" : "设备离线");
        return ok;
    }

    public boolean isOnline(String deviceSn) {
        return registry.isOnline(deviceSn);
    }

    /** 设备是否已跟踪某名称的食材（该冰箱下已认领的设备行中存在同名项） */
    public boolean hasDeviceItem(Long fridgeId, String name) {
        return ingredientMapper.selectCount(Wrappers.<Ingredient>lambdaQuery()
                .eq(Ingredient::getFridgeId, fridgeId)
                .eq(Ingredient::getName, name)
                .isNotNull(Ingredient::getDeviceItemId)) > 0;
    }

    /**
     * 校验设备属于指定家庭（小程序/Web 下发命令前的权限检查），
     * 设备不存在或不属于该家庭时抛业务异常
     */
    public Fridge requireOwnedDevice(String deviceSn, Long familyId) {
        Fridge fridge = findBySn(deviceSn);
        if (fridge == null) {
            throw new BusinessException(ResultCode.FRIDGE_NOT_FOUND);
        }
        if (!fridge.getFamilyId().equals(familyId)) {
            throw new BusinessException(ResultCode.FRIDGE_PERMISSION_DENIED);
        }
        return fridge;
    }

    private Fridge findBySn(String deviceSn) {
        return fridgeMapper.selectOne(Wrappers.<Fridge>lambdaQuery()
                .eq(Fridge::getDeviceSn, deviceSn)
                .last("LIMIT 1"));
    }

    /** 启动时装载系统分类映射；装载后不变，与"设备数据不反向改分类表"的约定一致 */
    @PostConstruct
    public void preloadSystemCategories() {
        for (Category c : categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getIsSystem, 1))) {
            systemCategories.put(c.getName(), c.getId());
        }
        log.info("[设备] 系统分类装载 {} 项: {}", systemCategories.size(), systemCategories.keySet());
    }

    private Ingredient insertIngredient(Long fridgeId, Long deviceItemId, String name,
                                        BigDecimal quantity, String unit, LocalDate expiry,
                                        BigDecimal confidence, Integer status) {
        // 摄像头 10 类食材：分类/保质期按定死映射表填写（保质期仅在其为空时兜底）
        int[] known = DEVICE_FOOD_MAP.get(name);
        Integer categoryId = known != null ? known[0] : classify(name);
        if (categoryId == null) {
            // 分类表没有可用系统分类：宁可不建账，也不写入 category_id=NULL 的脏行让列表页 500
            return null;
        }
        Ingredient ing = new Ingredient();
        ing.setFridgeId(fridgeId);
        ing.setDeviceItemId(deviceItemId);
        ing.setCategoryId(categoryId);
        if (known != null && expiry == null) {
            expiry = LocalDate.now().plusDays(known[1]);
        }
        ing.setName(name);
        ing.setQuantity(quantity);
        ing.setUnit(unit);
        ing.setStatus(status != null ? status : 1);
        ing.setExpiryDate(expiry);
        ing.setRecognitionMode("auto");
        ing.setConfidence(confidence);
        ingredientMapper.insert(ing);
        return ing;
    }

    /** 设备上报的中文名称/分类 → 系统分类ID（精确匹配 → 包含匹配 → 其他），始终返回非空 ID */
    private Integer classify(String text) {
        if (text != null) {
            Integer id = systemCategories.get(text);
            if (id != null) {
                return id;
            }
            for (Map.Entry<String, Integer> e : systemCategories.entrySet()) {
                if (text.contains(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        // 兜底必须回一个真实存在的分类：返回 null 会让 category_id 落空，
        // 而列表页按分类名展示、看板按分类分组，都假设分类存在
        Integer fallback = systemCategories.get("其他");
        if (fallback != null) {
            return fallback;
        }
        log.warn("[设备] 系统分类缺失（既无“其他”也无任何 is_system=1 分类），识别事件暂不入账: {}", text);
        return null;
    }

    private BigDecimal decimal(JsonNode node, BigDecimal fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 解析板端事件时间戳（Qt ISODate），失败返回 null */
    private LocalDateTime parseEventTime(JsonNode node) {
        String s = node == null || node.isNull() ? null : node.asText();
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(JsonNode node) {
        String s = node == null || node.isNull() ? null : node.asText();
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private String orDefault(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 向设备所属家庭的所有在线客户端广播动态（推送失败不影响主流程） */
    private void notifyFamily(Fridge fridge, Map<String, Object> payload) {
        try {
            clientRegistry.broadcast(fridge.getFamilyId(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.warn("[推送] 消息序列化失败: {}", payload);
        }
    }
}
