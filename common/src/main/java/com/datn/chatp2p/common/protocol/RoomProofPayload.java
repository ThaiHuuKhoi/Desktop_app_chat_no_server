package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@code EnvelopeType.ROOM_PROOF}: bang chung (HMAC, Base64) la ben gui biet mat khau
 * phong, gan voi khoa ECDH cua phien - xem {@code RoomSecret} o module crypto. Chi dung o phong
 * co mat khau.
 */
public record RoomProofPayload(String proof) {
}
