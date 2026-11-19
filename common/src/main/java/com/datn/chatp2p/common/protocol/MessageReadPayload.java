package com.datn.chatp2p.common.protocol;

import java.util.List;

/** Payload cua {@code MESSAGE_READ}: nguoi gui ban tin nay da xem cac tin {@code messageIds} cua ban. */
public record MessageReadPayload(List<String> messageIds) {
}
