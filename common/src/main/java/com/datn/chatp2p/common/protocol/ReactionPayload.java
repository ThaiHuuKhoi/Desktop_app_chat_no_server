package com.datn.chatp2p.common.protocol;

import java.util.List;

/**
 * Payload cua {@code MESSAGE_REACTION}: them ({@code added=true}) hoac bo 1 cam xuc tren tin
 * {@code messageId}. Chi chap nhan cac cam xuc trong {@link #ALLOWED} - khong cho chen chu tuy y.
 */
public record ReactionPayload(String messageId, String emoji, boolean added) {

    /** Bo cam xuc co dinh: thich, tim, cuoi, ngac nhien, buon. */
    public static final List<String> ALLOWED = List.of("👍", "❤️", "😂", "😮", "😢");
}
