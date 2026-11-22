package com.datn.chatp2p.signaling.room;

import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

/**
 * Mot peer dang ket noi toi signaling server: gan mot {@link WebSocketSession}
 * voi thong tin phong / danh tinh toi gian (khong luu gi ve noi dung chat).
 *
 * <p>{@code authenticated} / {@code identityKey}: nguoi nay vao bang token tai khoan hop le hay la
 * khach tu dat ten, va khoa danh tinh da dang ky cua tai khoan - gui kem PEER_LIST / PEER_JOINED de
 * nguoi trong phong phan biet tai khoan that voi khach dat trung ten, va tu doi chieu khoa trong
 * {@code PEER_IDENTITY} (Co-che-xac-thuc-danh-tinh.md Buoc 7).
 */
public final class PeerSession {

    private final WebSocketSession webSocketSession;
    private final String roomId;
    private final String peerId;
    private final String userName;
    private final boolean authenticated;
    private final String identityKey;

    public PeerSession(WebSocketSession webSocketSession, String roomId, String peerId, String userName) {
        this(webSocketSession, roomId, peerId, userName, false, null);
    }

    public PeerSession(WebSocketSession webSocketSession, String roomId, String peerId, String userName,
                       boolean authenticated, String identityKey) {
        this.webSocketSession = Objects.requireNonNull(webSocketSession, "webSocketSession");
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.peerId = Objects.requireNonNull(peerId, "peerId");
        this.userName = Objects.requireNonNull(userName, "userName");
        this.authenticated = authenticated;
        this.identityKey = authenticated ? identityKey : null;
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

    public boolean isAuthenticated() {
        return authenticated;
    }

    /** Khoa danh tinh (Base64 X.509) da dang ky cua tai khoan; null voi khach hoac tai khoan chua gan khoa. */
    public String getIdentityKey() {
        return identityKey;
    }
}
