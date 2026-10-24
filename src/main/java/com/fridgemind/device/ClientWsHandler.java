package com.fridgemind.device;

import com.fridgemind.entity.FamilyMember;
import com.fridgemind.mapper.FamilyMemberMapper;
import com.fridgemind.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 客户端实时推送处理器（浏览器/小程序）
 * 连接 URL：ws://host:9090/ws/client?token=&lt;JWT&gt;
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientWsHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final FamilyMemberMapper familyMemberMapper;
    private final ClientSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long familyId = resolveFamilyId(session);
        if (familyId == null) {
            // 认证失败（无 token/无效 token/未加入家庭）直接断开，不进入会话注册表
            log.warn("[推送] 未授权的客户端连接: {}", session.getRemoteAddress());
            closeQuietly(session);
            return;
        }
        registry.register(familyId, session);
        log.debug("[推送] 客户端接入 familyId={}, remote={}", familyId, session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeQuietly(session);
    }

    /** 解析 JWT → userId → 家庭ID；未登录/无效 token/未加入家庭 均拒绝 */
    private Long resolveFamilyId(WebSocketSession session) {
        String token = queryParam(session, "token");
        JwtUtil.TokenInfo info = token == null ? null : jwtUtil.parseToken(token);
        // 客户端推送只面向普通用户，管理员 Token 一律拒绝
        if (info == null || !"user".equals(info.role())) {
            return null;
        }
        FamilyMember member = familyMemberMapper.selectOneByUserId(info.userId());
        return member != null ? member.getFamilyId() : null;
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

    /** 以 POLICY_VIOLATION 语义关闭：告知对端是协议层拒绝而非网络异常 */
    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
    }
}
