package com.datn.chatp2p.common.signal;

/**
 * Cac loai thong diep trong giao thuc signaling toi gian (De-cuong-Chat-P2P-Java.md
 * muc 6): signaling server chi lam nhiem vu "gioi thieu" - relay cac ban tin nay
 * giua cac peer trong cung phong, khong bao gio doc/luu noi dung chat.
 */
public enum SignalType {
    /** Client -> Server: xin tham gia mot phong, kem ten hien thi. */
    JOIN,
    /** Client -> Server: roi phong (hoac ngat ket noi ngam dinh suy ra tu ws close). */
    LEAVE,
    /** Server -> Client: thong bao co peer moi vao phong. */
    PEER_JOINED,
    /** Server -> Client: thong bao mot peer da roi phong. */
    PEER_LEFT,
    /** Server -> Client: danh sach day du cac peer dang co trong phong. */
    PEER_LIST,
    /** Client -> Server -> Client dich: SDP offer (relay, khong doc noi dung). */
    OFFER,
    /** Client -> Server -> Client dich: SDP answer (relay, khong doc noi dung). */
    ANSWER,
    /** Client -> Server -> Client dich: ICE candidate (relay, khong doc noi dung). */
    ICE_CANDIDATE
}
