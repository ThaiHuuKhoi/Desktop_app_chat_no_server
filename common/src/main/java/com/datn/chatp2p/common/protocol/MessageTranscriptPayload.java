package com.datn.chatp2p.common.protocol;

import java.util.List;

/**
 * Payload cua {@code EnvelopeType.MESSAGE_TRANSCRIPT}: 1 phan lich su chat NHOM gui cho peer moi
 * vao phong (conversation backfilling). Lich su dai duoc chia nhieu goi de vua 1 datagram UDP;
 * moi goi duoc xac nhan bang {@code MESSAGE_ACK(batchId)} va gui lai neu mat.
 *
 * <p>Khong bao gio chua tin nhan rieng (DM). Ben nhan KHONG xac minh duoc nguoi viet cua tung
 * tin (chi biet ai chia se lich su) - giao dien phai danh dau la "lich su do X chia se".
 */
public record MessageTranscriptPayload(String batchId, List<HistoryMessage> messages) {

    /** 1 tin trong lich su; {@code authorName} de hien thi ca khi tac gia da roi phong. */
    public record HistoryMessage(String id, String authorId, String authorName, String text, long timeSent) {
    }
}
