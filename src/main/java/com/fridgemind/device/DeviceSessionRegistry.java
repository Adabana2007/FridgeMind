package com.fridgemind.device;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 设备会话注册表 */
@Component
public class DeviceSessionRegistry {

    /** 心跳超时时间：90 秒（3 个心跳周期）无心跳则视为掉线 */
    private static final long HEARTBEAT_TIMEOUT_MS = 90_000;

    private final Map<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册设备会话（同一设备重连时替换旧会话）
     */
    public void register(String deviceSn, WebSocketSession session) {
        DeviceSession old = sessions.put(deviceSn, new DeviceSession(session));
        // 覆盖式注册：同设备只保留一条活动连接，立刻关闭旧连接避免双连接并存/重复推送
        closeQuietly(old);
    }

    /**
     * 按会话对象注销（连接关闭时调用），返回该会话对应的 deviceSn，未注册返回 null
     */
    public String unregister(WebSocketSession session) {
        for (Map.Entry<String, DeviceSession> e : sessions.entrySet()) {
            if (e.getValue().session == session) {
                sessions.remove(e.getKey());
                return e.getKey();
            }
        }
        return null;
    }

    /** 刷新设备心跳时间 */
    public void touch(String deviceSn) {
        DeviceSession ds = sessions.get(deviceSn);
        if (ds != null) {
            ds.touch();
        }
    }

    public boolean isOnline(String deviceSn) {
        return sessions.containsKey(deviceSn);
    }

    /** 向设备推送文本消息，设备不在线或发送失败返回 false */
    public boolean send(String deviceSn, TextMessage message) {
        DeviceSession ds = sessions.get(deviceSn);
        if (ds == null || !ds.session.isOpen()) {
            return false;
        }
        try {
            // WebSocketSession.sendMessage 非线程安全：串行化避免多线程并发推送互相干扰
            synchronized (ds.session) {
                ds.session.sendMessage(message);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 找出心跳超时的设备并关闭其连接（关闭会触发 Handler 的断开回调） */
    public void closeStale() {
        long now = System.currentTimeMillis();
        List<String> stale = sessions.entrySet().stream()
                .filter(e -> now - e.getValue().lastSeen > HEARTBEAT_TIMEOUT_MS)
                .map(Map.Entry::getKey)
                .toList();
        stale.forEach(sn -> closeQuietly(sessions.remove(sn)));
    }

    private void closeQuietly(DeviceSession ds) {
        if (ds != null && ds.session.isOpen()) {
            try {
                ds.session.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** 单个设备的会话包装：会话 + 最后心跳时间 */
    @RequiredArgsConstructor
    private static class DeviceSession {
        private final WebSocketSession session;
        @Getter
        private volatile long lastSeen = System.currentTimeMillis();

        void touch() {
            lastSeen = System.currentTimeMillis();
        }
    }
}
