package com.datn.chatp2p.signaling.config;

import com.datn.chatp2p.signaling.ws.SignalingWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Dang ky endpoint WebSocket {@code /ws} cho giao thuc signaling. Cho phep moi
 * origin (setAllowedOriginPatterns("*")) vi day la mot server cong khai, toi
 * gian, khong xu ly du lieu nhay cam - dung theo tinh than "khong can API
 * server" / de tu-host cua chitchatter.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingWebSocketHandler signalingWebSocketHandler;

    public WebSocketConfig(SignalingWebSocketHandler signalingWebSocketHandler) {
        this.signalingWebSocketHandler = signalingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}
