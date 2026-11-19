package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@code EnvelopeType.TYPING_STATUS_CHANGE}. Gui "best-effort" (khong ACK): ben gui
 * nhac lai {@code typing = true} dinh ky khi van dang go, ben nhan tu het han sau vai giay -
 * mat goi chi lam chi bao "dang go" tre/tat som, khong sai lech lau.
 *
 * <p>{@link Envelope#namespace()} = null: dang go o khung chat nhom;
 * {@link EnvelopeNamespace#DIRECT_MESSAGE}: dang go trong tab tin nhan rieng voi nguoi nhan.
 */
public record TypingStatusPayload(boolean typing) {
}
