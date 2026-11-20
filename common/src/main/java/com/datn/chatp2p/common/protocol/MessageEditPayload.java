package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@code MESSAGE_EDIT}: tac gia sua ({@code deleted=false}, {@code text} moi) hoac xoa
 * ({@code deleted=true}) tin {@code messageId} cua chinh minh. Gui tin cay nhu MESSAGE: ben nhan tra
 * {@code MESSAGE_ACK(editId)}; {@code editId} moi lan sua mot khac de loai ban gui lai.
 */
public record MessageEditPayload(String editId, String messageId, String text, boolean deleted) {
}
