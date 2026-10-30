package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;

/** Su kien tu {@link ChatSession} gui len giao dien. Co the duoc goi tu thread khac JavaFX. */
public interface ChatSessionListener {

    /** 1 peer da ket noi xong VA trao khoa xong - gui/nhan tin duoc ngay. */
    void onPeerJoined(SessionPeer peer);

    /** Called when the peer's long-lived identity key has been verified. */
    default void onPeerIdentityUpdated(SessionPeer peer) {
    }

    void onPeerLeft(String peerId);

    void onMessageReceived(ChatMessage message);

    /** Khong ket noi duoc voi 1 peer; {@code reason} da viet cho nguoi dung doc. */
    void onPeerConnectionFailed(String peerId, String reason);
}
