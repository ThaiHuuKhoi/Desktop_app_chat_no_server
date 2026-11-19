package com.datn.chatp2p.client.session;

/**
 * Thong tin nguoi dung nhap o man hinh Home de vao phong.
 *
 * @param serverUri    dia chi signaling server (vd. {@code wss://chat.zingme.fun}).
 * @param roomPassword mat khau phong; rong = phong cong khai. Khong bao gio gui len server
 *                     (xem {@code RoomSecret}).
 */
public record RoomConfig(String userName, String roomName, String serverUri, String authToken, String roomPassword) {

    /** Ky tu ngan cach ten phong va "the" mat khau trong roomId gui len server. */
    public static final char PRIVATE_ROOM_SEPARATOR = '~';

    public RoomConfig(String userName, String roomName, String serverUri, String authToken) {
        this(userName, roomName, serverUri, authToken, "");
    }

    public RoomConfig(String userName, String roomName, String serverUri) {
        this(userName, roomName, serverUri, "", "");
    }

    public boolean isPasswordProtected() {
        return roomPassword != null && !roomPassword.isEmpty();
    }

    /** Khong in mat khau ra log. */
    @Override
    public String toString() {
        return "RoomConfig[userName=" + userName + ", roomName=" + roomName + ", serverUri=" + serverUri
                + ", passwordProtected=" + isPasswordProtected() + "]";
    }
}
