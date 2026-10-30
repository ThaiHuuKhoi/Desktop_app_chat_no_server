package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@code EnvelopeType.MESSAGE} - tin nhan van ban, nhom hoac direct
 * message (phan biet qua {@link Envelope#namespace()}) - Tai-lieu-ky-thuat.md
 * Phan E.3.3.
 */
public record MessagePayload(String id, String authorId, String text, long timeSent) {
}
