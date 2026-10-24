package com.fridgemind.device;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 浏览器/小程序客户端会话注册表（实时推送用）
 * 维护 familyId → 已登录客户端会话，并向家庭内所有会话广播。
 */
@Component
public class ClientSessionRegistry {

    private final Map<Long, CopyOnWriteArraySet<WebSocketSession>> byFamily = new ConcurrentHashMap<>();

    /** 注册客户端会话到其所属家庭：同家庭可多端并存，广播时会同时推送 */
    public void register(Long familyId, WebSocketSession session) {
        byFamily.computeIfAbsent(familyId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /** 按会话对象注销，返回其所属 familyId */
    public Long unregister(WebSocketSession session) {
        for (Map.Entry<Long, CopyOnWriteArraySet<WebSocketSession>> e : byFamily.entrySet()) {
            if (e.getValue().remove(session)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 向家庭内所有在线客户端广播文本消息 */
    public void broadcast(Long familyId, String json) {
        CopyOnWriteArraySet<WebSocketSession> sessions = byFamily.get(familyId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            // 惰性清理：已关闭或发送失败的会话直接从集合移除，避免越积越多
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                sessions.remove(session);
            }
        }
    }
}
