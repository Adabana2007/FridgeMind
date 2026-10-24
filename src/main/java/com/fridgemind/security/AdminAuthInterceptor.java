package com.fridgemind.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgemind.common.Result;
import com.fridgemind.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 管理员认证拦截器
 * 拦截 /api/admin/**，校验 Token 中 role=admin。
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED, "缺少认证信息"));
            return false;
        }

        // 去掉 "Bearer " 前缀（7 个字符）
        String token = authHeader.substring(7);
        JwtUtil.TokenInfo tokenInfo = jwtUtil.parseToken(token);
        if (tokenInfo == null) {
            writeJson(response, Result.error(ResultCode.UNAUTHORIZED, "Token无效或已过期"));
            return false;
        }

        // 只允许管理员 Token，拒绝普通用户 Token
        if (!"admin".equals(tokenInfo.role())) {
            writeJson(response, Result.error(ResultCode.FORBIDDEN, "无权访问管理端"));
            return false;
        }

        // 将管理员 ID 存入上下文
        AuthContext.setUserId(tokenInfo.userId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程池复用时数据泄漏到下一次请求
        AuthContext.clear();
    }

    /** 管理端错误统一回 401，具体错误由 body.code 区分（缺少认证/无效 token/无权访问） */
    private void writeJson(HttpServletResponse response, Result<?> result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
