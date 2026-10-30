package com.datn.chatp2p.client.session;

/**
 * Thong tin nguoi dung nhap o man hinh Home de vao phong.
 *
 * @param serverUri dia chi signaling server (vd. {@code ws://localhost:8080}).
 */
public record RoomConfig(String userName, String roomName, String serverUri) {
}
