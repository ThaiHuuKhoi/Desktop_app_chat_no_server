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

    /** Tin nhan rieng, tach khoi transcript nhom. */
    default void onDirectMessageReceived(String peerId, ChatMessage message) {
    }

    /**
     * Nhan lich su chat nhom do {@code fromPeerId} chia se (khi minh moi vao phong). Chi gom cac
     * tin CHUA co; KHONG xac minh duoc nguoi viet tung tin - giao dien phai danh dau la lich su.
     *
     * @param authorNames ten hien thi theo authorId (ke ca nguoi da roi phong).
     */
    default void onHistoryReceived(String fromPeerId, java.util.List<ChatMessage> messages,
                                   java.util.Map<String, String> authorNames) {
    }

    /**
     * 1 peer bat dau / ngung go.
     *
     * @param direct {@code true} neu peer dang go trong tab tin nhan rieng voi minh.
     */
    default void onPeerTyping(String peerId, boolean direct, boolean typing) {
    }

    /**
     * 1 lan truyen file thay doi trang thai: loi moi moi ({@code OFFERED}), dang
     * cho tra loi, tien do, hoan tat, bi tu choi / huy / loi.
     */
    default void onFileTransferUpdated(FileTransferStatus status) {
    }

    /** Khong ket noi duoc voi 1 peer; {@code reason} da viet cho nguoi dung doc. */
    void onPeerConnectionFailed(String peerId, String reason);
}
