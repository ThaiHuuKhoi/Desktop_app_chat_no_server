package com.datn.chatp2p.signaling.room;

import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

/**
 * Mot peer dang ket noi toi signaling server: gan mot {@link WebSocketSession}
 * voi thong tin phong / danh tinh toi gian (khong luu gi ve noi dung chat).
 */
public final class PeerSession {

    private final WebSocketSession webSocketSession;
    private final String roomId;
    private final String peerId;
    private final String userName;

    public PeerSession(WebSocketSession webSocketSession, String roomId, String peerId, String userName) {
        this.webSocketSession = Objects.requireNonNull(webSocketSession, "webSocketSession");
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.peerId = Objects.requireNonNull(peerId, "peerId");
        this.userName = Objects.requireNonNull(userName, "userName");
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getUserName() {
        return userName;
    }
}
