package com.datn.chatp2p.common.model;

/**
 * Trang thai xac thuc danh tinh doi phuong bang chu ky identity ECDSA
 * (xem Tai-lieu-ky-thuat.md muc E.5).
 */
public enum PeerVerificationState {
    /** Dang cho peer gui identity payload va hoan tat xac minh. */
    VERIFYING,
    /** Identity chua toi hoac chu ky khong hop le. */
    UNVERIFIED,
    /** Chu ky identity hop le voi challenge cua phong. */
    VERIFIED
}
