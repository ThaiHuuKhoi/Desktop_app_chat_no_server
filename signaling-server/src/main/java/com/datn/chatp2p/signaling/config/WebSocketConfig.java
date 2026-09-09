package com.datn.chatp2p.signaling.config;

import com.datn.chatp2p.signaling.ws.SignalingWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Dang ky endpoint WebSocket {@code /ws} cho giao thuc signaling. Cho phep moi
 * origin (setAllowedOriginPatterns("*")) vi day la mot server cong khai, toi
 * gian, khong xu ly du lieu nhay cam - dung theo tinh than "khong can API
 * server" / de tu-host cua chitchatter.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Tomcat embedded mac dinh gioi han buffer nhan ban tin text CHI 8192 byte
     * ({@code maxTextMessageBufferSize}) - neu client gui 1 ban tin VUOT NGUONG
     * nay (vi du {@code IceOfferPayload} co qua nhieu candidate tren may co nhieu
     * adapter mang ao - VPN/Docker/WSL/Hyper-V), Tomcat KHONG cat bot hay tu bo
     * qua ban tin do ma DONG LUON ket noi cua chinh nguoi gui - da xac nhan that
     * bang {@code SignalingWebSocketHandlerTest#relaysALargeIceOfferPayloadOverARealWebSocketConnection}
     * (lan chay dau that bai voi 20.000 ky tu: nguoi gui bi vang khoi phong ngay
     * lap tuc thay vi OFFER duoc relay). Tang len 65536 byte (64KB) - du du cho
     * so luong candidate ICE thuc te (Tai-lieu-ky-thuat.md khong dinh nghia gioi
     * han cu the, day la muc chon co chu dich, van co tran de tranh 1 client gui
     * payload khong lo qua kenh signaling (file/media phai di qua P2P DataChannel,
     * khong phai signaling).
     */
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE_BYTES = 65536;

    private final SignalingWebSocketHandler signalingWebSocketHandler;

    public WebSocketConfig(SignalingWebSocketHandler signalingWebSocketHandler) {
        this.signalingWebSocketHandler = signalingWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }

    /** Spring/Tomcat tu doc bean nay de cau hinh gioi han buffer that su cho container WebSocket embedded. */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE_BYTES);
        return container;
    }
}
