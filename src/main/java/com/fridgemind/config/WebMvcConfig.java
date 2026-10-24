package com.fridgemind.config;

import com.fridgemind.security.AdminAuthInterceptor;
import com.fridgemind.security.AuthInterceptor;
import com.fridgemind.security.FamilyInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** WebMvc 配置：注册认证、家庭、管理员拦截器 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final FamilyInterceptor familyInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 按注册顺序执行：Auth(用户登录态) -> Family(家庭上下文) -> Admin(管理员)。/api/admin/** 不走用户认证，只走管理员拦截器。
        // 用户认证拦截器：排除登录入口
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/send-code",
                        "/api/user/login-by-code",
                        "/api/user/login-by-pwd",
                        "/api/admin/**"
                );

        // 家庭拦截器：额外排除无需家庭上下文的操作
        registry.addInterceptor(familyInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/send-code",
                        "/api/user/login-by-code",
                        "/api/user/login-by-pwd",
                        "/api/user/set-password",
                        "/api/user/info",
                        "/api/family/create",
                        "/api/family/join",
                        "/api/family/my",
                        "/api/family/members",
                        "/api/family/qr-code",
                        "/api/user/feedback",
                        "/api/admin/**"
                );

        // 管理员认证拦截器：拦截 /api/admin/**，排除登录
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/login"
                );
    }
}
