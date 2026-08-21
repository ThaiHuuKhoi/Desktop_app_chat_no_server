package com.datn.chatp2p.common.model;

/**
 * Trang thai xac thuc danh tinh doi phuong qua "van tay" khoa cong khai
 * (xem De-cuong-Chat-P2P-Java.md muc 2, muc 5). Phong theo
 * {@code PeerVerificationState} trong {@code models/chat.ts} cua chitchatter.
 */
public enum PeerVerificationState {
    /** Dang cho nguoi dung tu tay so khop fingerprint. */
    VERIFYING,
    /** Nguoi dung chua xac thuc / xac thuc that bai. */
    UNVERIFIED,
    /** Nguoi dung da xac nhan fingerprint khop. */
    VERIFIED
}
