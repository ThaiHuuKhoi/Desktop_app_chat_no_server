package com.datn.chatp2p.common.signal;

/**
 * Ly do server tu choi JOIN - gia tri {@code payload} cua ban tin
 * {@link SignalType#JOIN_REJECTED} (Tai-lieu-ky-thuat.md muc J.2).
 */
public final class JoinRejectReason {

    /** Dang co phong co mat khau cung ten nhung khac "the" (mat khau sai). */
    public static final String WRONG_PASSWORD = "WRONG_PASSWORD";
    /** Phong da du so nguoi toi da. */
    public static final String ROOM_FULL = "ROOM_FULL";
    /** Server yeu cau dang nhap nhung JOIN khong co token. */
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    /** Token khong hop le hoac da het han. */
    public static final String AUTH_EXPIRED = "AUTH_EXPIRED";
    /** peerId da co trong phong. */
    public static final String DUPLICATE_PEER = "DUPLICATE_PEER";
    /** Thu sai mat khau qua nhieu lan trong thoi gian ngan. */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** JOIN thieu roomId / peerId / ten hien thi. */
    public static final String INVALID_JOIN = "INVALID_JOIN";
    /** Server da dat gioi han tong so phong / so ket noi - thu lai sau. */
    public static final String SERVER_BUSY = "SERVER_BUSY";

    private JoinRejectReason() {
    }
}
