package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.model.ChatMessage;

/**
 * 1 dong trong khung chat: tin nhan cua nguoi dung, hoac dong he thong
 * ("Khoi da vao phong", "Da thiet lap kenh ma hoa"...) giup nguoi dung THAY
 * duoc vong doi ket noi P2P.
 */
sealed interface TranscriptEntry {

    record UserMessage(ChatMessage message) implements TranscriptEntry {
    }

    record SystemNotice(NoticeKind kind, String text) implements TranscriptEntry {
    }

    enum NoticeKind {
        INFO, SECURE, JOINED, LEFT, WARNING
    }
}
