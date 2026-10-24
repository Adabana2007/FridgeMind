package com.fridgemind.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fridgemind.common.Result;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 认证拦截器
 * 未携带有效 Token 的请求直接返回 401，不进入 Controller。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 放行 OPTIONS 预检请求（CORS）
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

        // 只允许普通用户 Token，拒绝管理员 Token 访问用户接口
        if (!"user".equals(tokenInfo.role())) {
            writeJson(response, Result.error(ResultCode.FORBIDDEN, "无权访问"));
            return false;
        }

        // 与 Redis 存储的 token 比对：支持登出/强制下线；键不存在（清库/过期）时降级放行，防全局锁死
        try {
            Object stored = redisTemplate.opsForValue().get("token:" + tokenInfo.userId());
            if (stored != null && !token.equals(stored.toString())) {
                writeJson(response, Result.error(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录"));
                return false;
            }
        } catch (Exception e) {
            log.warn("Redis 校验 token 失败，按 JWT 降级放行: {}", e.getMessage());
        }

        // 每次请求回查 enabled：管理员禁用后旧 token 立即失效，无需等 7 天过期
        User user = userMapper.selectById(tokenInfo.userId());
        if (user == null || (user.getEnabled() != null && user.getEnabled() == 0)) {
            writeJson(response, Result.error(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员"));
            return false;
        }

        // 将 userId 存入线程上下文，后续 FamilyInterceptor 和 Service 层可用
        AuthContext.setUserId(tokenInfo.userId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 确保 ThreadLocal 被清理，防止在线程池中泄漏到下一次请求
        AuthContext.clear();
    }

    /** 向响应写入 JSON 格式的错误信息，并根据错误码设置合适的 HTTP 状态码 */
    private void writeJson(HttpServletResponse response, Result<?> result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        // FORBIDDEN → 403，其余认证失败统一 401
        int httpStatus = result.getCode() == ResultCode.FORBIDDEN.getCode()
                ? HttpServletResponse.SC_FORBIDDEN
                : HttpServletResponse.SC_UNAUTHORIZED;
        response.setStatus(httpStatus);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
