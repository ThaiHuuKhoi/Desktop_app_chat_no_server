package com.datn.chatp2p.common.protocol;

/**
 * Tuong duong "PeerAction" cua chitchatter (xem Tai-lieu-ky-thuat.md Phan D.4,
 * E.3.1) - moi gia tri la 1 "kenh logic" doc lap tren cung 1 {@code DataChannel}
 * vat ly, phan biet bang truong {@code type} cua {@link Envelope}.
 *
 * <p>Chi cac gia tri {@code MESSAGE}, {@code MESSAGE_TRANSCRIPT},
 * {@code TYPING_STATUS_CHANGE}, {@code PEER_IDENTITY} co payload record da
 * dinh nghia trong module nay tinh den hien tai; cac gia tri con lai (truyen
 * file, media) da liet ke san theo thiet ke o Tai-lieu-ky-thuat.md Phan E.3.3
 * nhung payload cu the se do B/A bo sung khi cai dat chuc nang tuong ung.
 */
public enum EnvelopeType {
    /** Tin nhan van ban (nhom hoac direct message, phan biet qua {@link Envelope#namespace()}). */
    MESSAGE,
    /** Backfill lich su chat cho peer moi vao phong cong khai. */
    MESSAGE_TRANSCRIPT,
    /** Trang thai dang go. */
    TYPING_STATUS_CHANGE,
    /** Public key + chu ky danh tinh - xac thuc peer tu dong (chua co IdentitySignatureService). */
    PEER_IDENTITY,
    /** Rao 1 file dang chia se (hoac fileId=null de thu hoi). */
    FILE_OFFER,
    /** 1 chunk du lieu file da ma hoa. */
    FILE_CHUNK,
    /** Bat/tat mic. */
    AUDIO_CHANGE,
    /** Bat/tat webcam. */
    VIDEO_CHANGE,
    /** Bat/tat chia se man hinh. */
    SCREEN_SHARE_CHANGE,
    /** 1 khung hinh video/screen-share (Motion-JPEG hoac PCM). */
    MEDIA_FRAME
}
