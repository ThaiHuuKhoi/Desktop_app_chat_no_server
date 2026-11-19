package com.datn.chatp2p.client.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trang thai giao cua tung tin MINH gui (dua tren MESSAGE_ACK) - tach khoi JavaFX de test duoc.
 * Chi dung tren JavaFX thread.
 */
final class DeliveryStatuses {

    /** Trang thai hien duoi bong bong tin. */
    record Status(int recipients, int delivered, int failed) {

        enum Kind { NO_RECIPIENT, SENDING, PARTIAL, DELIVERED, FAILED }

        Kind kind() {
            if (recipients == 0) {
                return Kind.NO_RECIPIENT;
            }
            if (delivered == recipients) {
                return Kind.DELIVERED;
            }
            if (delivered + failed == recipients) {
                return delivered == 0 ? Kind.FAILED : Kind.PARTIAL;
            }
            return delivered == 0 ? Kind.SENDING : Kind.PARTIAL;
        }

        /** Chu ngan hien canh gio gui. */
        String label() {
            return switch (kind()) {
                case NO_RECIPIENT -> "";
                case SENDING -> "Đang gửi";
                case DELIVERED -> recipients == 1 ? "Đã nhận" : "Tất cả đã nhận";
                case PARTIAL -> "Đã nhận " + delivered + "/" + recipients;
                case FAILED -> "Chưa tới nơi";
            };
        }
    }

    private static final class Entry {
        final Set<String> recipients;
        final Set<String> delivered = new HashSet<>();
        final Set<String> failed = new HashSet<>();

        Entry(List<String> recipients) {
            this.recipients = new HashSet<>(recipients);
        }

        Status status() {
            return new Status(recipients.size(), delivered.size(), failed.size());
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    void sent(String messageId, List<String> recipients) {
        entries.put(messageId, new Entry(recipients));
    }

    /** @return {@code true} neu trang thai thay doi (can ve lai). */
    boolean delivered(String messageId, String peerId) {
        Entry entry = entries.get(messageId);
        if (entry == null || !entry.recipients.contains(peerId)) {
            return false;
        }
        entry.failed.remove(peerId); // ACK toi muon sau khi da bao that bai van tinh la da nhan
        return entry.delivered.add(peerId);
    }

    /** @return {@code true} neu trang thai thay doi. */
    boolean failed(String messageId, String peerId) {
        Entry entry = entries.get(messageId);
        if (entry == null || !entry.recipients.contains(peerId) || entry.delivered.contains(peerId)) {
            return false;
        }
        return entry.failed.add(peerId);
    }

    /** Nguoi nhan roi phong truoc khi xac nhan -&gt; tinh la khong toi duoc (khong treo "Dang gui" mai). */
    boolean peerLeft(String peerId) {
        boolean changed = false;
        for (Entry entry : entries.values()) {
            if (entry.recipients.contains(peerId) && !entry.delivered.contains(peerId)) {
                changed |= entry.failed.add(peerId);
            }
        }
        return changed;
    }

    /** null neu khong phai tin minh gui trong phien nay (vd tin cu tu lich su). */
    Status status(String messageId) {
        Entry entry = entries.get(messageId);
        return entry == null ? null : entry.status();
    }
}
