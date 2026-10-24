package com.fridgemind.device;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket 配置：装配 /ws 设备网关与 /ws/client 客户端推送端点 */
@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWsHandler deviceWsHandler;
    private final ClientWsHandler clientWsHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 放行任意来源：设备固件、浏览器/小程序与后端跨端口连接
        registry.addHandler(deviceWsHandler, "/ws")
                .setAllowedOriginPatterns("*");
        registry.addHandler(clientWsHandler, "/ws/client")
                .setAllowedOriginPatterns("*");
    }
}
