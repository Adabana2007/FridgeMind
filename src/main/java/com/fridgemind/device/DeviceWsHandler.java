package com.fridgemind.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 设备 WebSocket 处理器（接入层）：只管理连接生命周期、解析 JSON 信封（type 字段）并按类型转交，
 * 不读不写任何业务表，业务语义全部在 {@link DeviceSyncService}。
 * 设备连接 URL：ws://host:9090/ws?type=fridge&deviceId=fridge_001
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceWsHandler extends TextWebSocketHandler {

    private final DeviceSessionRegistry registry;
    private final DeviceSyncService syncService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String deviceSn = queryParam(session, "deviceId");
        String type = queryParam(session, "type");
        // 设备白名单 + 密钥校验：未注册的 device_sn 或密钥不符直接拒绝
        if (!"fridge".equals(type) || deviceSn == null
                || !syncService.canAccept(deviceSn, queryParam(session, "key"))) {
            log.warn("[设备] 拒绝连接: type={}, deviceId={}, remote={}", type, deviceSn, session.getRemoteAddress());
            closeQuietly(session);
            return;
        }
        registry.register(deviceSn, session);
        log.info("[设备] 连接建立: {} from {}", deviceSn, session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("[设备] 非 JSON 消息，忽略: {}", message.getPayload());
            return;
        }
        String type = root.path("type").asText("");
        try {
            switch (type) {
                case "register" -> syncService.onRegister(queryParam(session, "deviceId"),
                        root.path("deviceName").asText(""));
                case "heartbeat" -> {
                    registry.touch(queryParam(session, "deviceId"));
                    syncService.onHeartbeat(queryParam(session, "deviceId"));
                }
                case "inventory_update" -> syncService.onInventory(queryParam(session, "deviceId"),
                        root.path("items"));
                case "new_event" -> syncService.onEvent(queryParam(session, "deviceId"),
                        root.path("event"));
                case "remote_add_ack", "remote_remove_ack" ->
                        log.info("[设备] 收到回执: {}", message.getPayload());
                default -> log.debug("[设备] 未知消息类型: {}", type);
            }
        } catch (Exception e) {
            // 单条消息处理失败不影响连接
            log.error("[设备] 消息处理异常: {}", message.getPayload(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 仅当该会话仍被注册表持有才触发离线：设备重连时旧连接已被 register 替换出表，
        // unregister 返回 null，避免把刚重连的设备误标离线
        String deviceSn = registry.unregister(session);
        if (deviceSn != null) {
            syncService.onDisconnect(deviceSn);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[设备] 连接异常: {}", exception.getMessage());
        closeQuietly(session);
    }

    private String queryParam(WebSocketSession session, String name) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }
}
