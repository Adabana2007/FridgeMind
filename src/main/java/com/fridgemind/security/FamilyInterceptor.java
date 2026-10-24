package com.fridgemind.security;

import com.fridgemind.entity.FamilyMember;
import com.fridgemind.mapper.FamilyMemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 家庭上下文拦截器
 * 根据已解析的 userId 查询家庭成员信息，将 familyId 存入 AuthContext。
 */
@Component
@RequiredArgsConstructor
public class FamilyInterceptor implements HandlerInterceptor {

    private final FamilyMemberMapper familyMemberMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // 无登录态（公开接口）无需注入 familyId，直接放行
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return true;
        }

        FamilyMember member = familyMemberMapper.selectOneByUserId(userId);
        if (member != null) {
            AuthContext.setFamilyId(member.getFamilyId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束后清理 ThreadLocal，防止线程池复用时数据污染
        AuthContext.clear();
    }
}
