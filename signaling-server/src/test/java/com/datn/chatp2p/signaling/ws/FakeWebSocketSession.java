package com.datn.chatp2p.signaling.ws;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link WebSocketSession} gia lap CHI DUNG CHO TEST - tu viet tay thay vi
 * dung Mockito, vi Mockito (mock maker "inline", dua tren Byte Buddy) chua ho
 * tro JDK qua moi (da xac nhan that: JDK 26 that bai voi loi "Java 26 (70) is
 * not supported by the current version of Byte Buddy which officially
 * supports Java 23 (67)" khi chay SignalingWebSocketHandlerErrorHandlingTest).
 *
 * <p>Chi cai dat that {@code getId}, {@code getAttributes}, {@code isOpen},
 * {@code sendMessage}, {@code close} - dung method ma {@link SignalingWebSocketHandler}
 * thuc su goi toi; cac method con lai cua {@link WebSocketSession} nem
 * {@link UnsupportedOperationException} vi khong bao gio duoc goi trong pham
 * vi test nay (neu goi nham, se bao loi ro rang thay vi tra ve gia tri sai).
 */
final class FakeWebSocketSession implements WebSocketSession {

    private final String id;
    private final Map<String, Object> attributes = new HashMap<>();
    private final List<WebSocketMessage<?>> sentMessages = new ArrayList<>();
    private volatile boolean open = true;
    private volatile IOException sendFailure;

    FakeWebSocketSession(String id) {
        this.id = id;
    }

    /** Tu lan goi sendMessage() tiep theo tro di, nem {@code exception} thay vi gui that - mo phong socket dang loi. */
    void failSendsWith(IOException exception) {
        this.sendFailure = exception;
    }

    List<WebSocketMessage<?>> getSentMessages() {
        return sentMessages;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (sendFailure != null) {
            throw sendFailure;
        }
        sentMessages.add(message);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public void close(CloseStatus status) {
        open = false;
    }

    @Override
    public URI getUri() {
        throw new UnsupportedOperationException("Khong dung trong test nay");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        throw new UnsupportedOperationException("Khong dung trong test nay");
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        throw new UnsupportedOperationException("Khong dung trong test nay");
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException("Khong dung trong test nay");
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        // khong dung trong test nay
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 0;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        // khong dung trong test nay
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 0;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return List.of();
    }
}
