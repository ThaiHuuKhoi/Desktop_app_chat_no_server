package com.datn.chatp2p.client.session;

/**
 * Thong tin nguoi dung nhap o man hinh Home de vao phong.
 *
 * @param serverUri dia chi signaling server (vd. {@code ws://localhost:8080});
 *                  khong dung o che do demo.
 * @param demoMode  {@code true}: dung peer gia lap, khong can mang.
 */
public record RoomConfig(String userName, String roomName, String serverUri, boolean demoMode) {
}
