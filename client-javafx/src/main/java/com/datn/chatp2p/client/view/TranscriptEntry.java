package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.common.model.ChatMessage;

/**
 * 1 dong trong khung chat: tin nhan cua nguoi dung, hoac dong he thong
 * ("Khoi da vao phong", "Da thiet lap kenh ma hoa"...) giup nguoi dung THAY
 * duoc vong doi ket noi P2P.
 */
sealed interface TranscriptEntry {

    /**
     * @param sharedBy null voi tin nhan truc tiep; ten nguoi chia se neu tin den tu lich su
     *                 (khong xac minh duoc nguoi viet - giao dien danh dau rieng).
     */
    record UserMessage(ChatMessage message, String sharedBy) implements TranscriptEntry {

        /** {@code sharedBy} dac biet: tin nap tu lich su luu tren may (khong phai ai chia se). */
        static final String SAVED_ON_DEVICE = "\u0000saved-on-device";

        UserMessage(ChatMessage message) {
            this(message, null);
        }

        boolean fromHistory() {
            return sharedBy != null;
        }
    }

    record SystemNotice(NoticeKind kind, String text) implements TranscriptEntry {
    }

    /** The file dang gui / nhan - duoc thay bang ban moi moi khi trang thai doi. */
    record FileTransfer(FileTransferStatus status) implements TranscriptEntry {
    }

    enum NoticeKind {
        INFO, SECURE, JOINED, LEFT, WARNING
    }
}
