package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.MessageTranscriptPayload.HistoryMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lich su chat NHOM cua phien (khong gom tin nhan rieng), dung de gui cho peer moi vao phong
 * (conversation backfilling, Tai-lieu-ky-thuat.md muc E.4 buoc 6). Chi nam trong RAM, mat khi
 * roi phong - dung tinh than ephemeral. Thread-safe.
 */
final class GroupHistory {

    /** Giu toi da bay nhieu tin gan nhat. */
    static final int MAX_MESSAGES = 200;
    /** Tong so ky tu (text + ten) toi da trong 1 goi MESSAGE_TRANSCRIPT. */
    static final int BATCH_CHAR_BUDGET = 6_000;
    /**
     * Tin dai hon gioi han nay khong dua vao lich su: 12 000 ky tu UTF-8 (toi da 3 byte) + Base64
     * cua Envelope van duoi gioi han 65 507 byte cua 1 datagram.
     */
    static final int MAX_TEXT_CHARS = 12_000;

    private final List<HistoryMessage> messages = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();

    /** @return {@code true} neu la tin moi (chua co id nay) va da duoc them. */
    synchronized boolean add(HistoryMessage message) {
        if (!isAcceptable(message) || !ids.add(message.id())) {
            return false;
        }
        int index = messages.size();
        while (index > 0 && messages.get(index - 1).timeSent() > message.timeSent()) {
            index--; // giu thu tu theo thoi gian (lich su cua nguoi khac co the xen giua)
        }
        messages.add(index, message);
        while (messages.size() > MAX_MESSAGES) {
            ids.remove(messages.remove(0).id());
        }
        return true;
    }

    synchronized boolean contains(String id) {
        return ids.contains(id);
    }

    synchronized List<HistoryMessage> snapshot() {
        return List.copyOf(messages);
    }

    static boolean isAcceptable(HistoryMessage message) {
        return message != null && message.id() != null && !message.id().isBlank()
                && message.authorId() != null && message.text() != null
                && message.text().length() <= MAX_TEXT_CHARS;
    }

    /** Chia lich su thanh cac goi vua 1 datagram; tin dai co the nam 1 minh 1 goi. */
    static List<List<HistoryMessage>> batches(List<HistoryMessage> history) {
        List<List<HistoryMessage>> result = new ArrayList<>();
        List<HistoryMessage> current = new ArrayList<>();
        int chars = 0;
        for (HistoryMessage message : history) {
            int size = message.text().length() + (message.authorName() == null ? 0 : message.authorName().length());
            if (!current.isEmpty() && chars + size > BATCH_CHAR_BUDGET) {
                result.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(message);
            chars += size;
        }
        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }
}
