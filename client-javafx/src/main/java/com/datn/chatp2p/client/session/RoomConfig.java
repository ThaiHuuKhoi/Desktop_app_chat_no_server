package com.datn.chatp2p.client.session;

/**
 * Thong tin nguoi dung nhap o man hinh Home de vao phong.
 *
 * @param serverUri dia chi signaling server (vd. {@code ws://165.101.47.83:8080}).
 */
public record RoomConfig(String userName, String roomName, String serverUri, String authToken) {
    public RoomConfig(String userName, String roomName, String serverUri) {
        this(userName, roomName, serverUri, "");
    }
}
