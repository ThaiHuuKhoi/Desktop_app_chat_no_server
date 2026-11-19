package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@code EnvelopeType.MESSAGE_ACK}: ben nhan bao lai {@code messageId}
 * (= {@link MessagePayload#id()}) da toi noi, de ben gui ngung gui lai.
 */
public record MessageAckPayload(String messageId) {
}
