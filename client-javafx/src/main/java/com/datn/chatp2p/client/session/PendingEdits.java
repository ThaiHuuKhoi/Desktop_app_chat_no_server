package com.datn.chatp2p.client.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Lenh sua / xoa toi TRUOC tin goc (UDP dao thu tu: goi dau cua tin bi rot, ben gui gui lai sau 500 ms,
 * trong luc do lenh xoa da toi). Truoc day lenh nhu vay bi bo qua nhung van tinh la "da nhan", nen ban
 * gui lai cung bi bo - tin goc toi sau duoc hien va giu mai, va lot vao lich su nhom chia cho nguoi vao sau.
 *
 * <p>Nay lenh toi som duoc giu tam o day; tin goc toi thi {@link #take} lay ra va ap dung truoc khi hien.
 *
 * <ul>
 *   <li>Khoa = (tac gia, ma tin, nhom/rieng) - giu dung luat "chi tac gia sua duoc", lenh trong tin rieng
 *       khong ap nham sang tin nhom.</li>
 *   <li>Moi muc song {@value #TTL_MILLIS} ms - tin goc gui lai toi da ~3 s, qua lau coi nhu khong bao gio toi.</li>
 *   <li>Toi da {@value #MAX_PER_PEER} muc moi nguoi gui, day thi bo muc cu nhat - 1 thanh vien xau gui
 *       hang nghin lenh xoa cho tin khong co that khong lam phinh bo nho may nguoi khac.</li>
 *   <li>Nhieu lenh cho cung 1 tin: XOA THANG - da co lenh xoa thi lenh sua toi sau bi bo qua.</li>
 * </ul>
 *
 * Gioi han con lai: 2 lan SUA lien tiep toi dao thu tu thi giu ban toi sau cung (lenh sua chua co so thu tu).
 */
final class PendingEdits {

    static final long TTL_MILLIS = 60_000;
    static final int MAX_PER_PEER = 50;

    /** {@code text} null khi {@code deleted}. */
    record Edit(String text, boolean deleted, long receivedAt) {
    }

    private record Key(String messageId, boolean direct) {
    }

    private final LongSupplier clock;
    private final Map<String, LinkedHashMap<Key, Edit>> byPeer = new HashMap<>();

    PendingEdits(LongSupplier clock) {
        this.clock = clock;
    }

    synchronized void park(String peerId, String messageId, boolean direct, String text, boolean deleted) {
        long now = clock.getAsLong();
        LinkedHashMap<Key, Edit> edits = byPeer.computeIfAbsent(peerId, id -> new LinkedHashMap<>());
        pruneExpired(edits, now);
        Key key = new Key(messageId, direct);
        Edit existing = edits.get(key);
        if (existing != null && existing.deleted()) {
            return; // xoa thang
        }
        edits.remove(key); // dua ve cuoi hang (moi nhat)
        edits.put(key, new Edit(deleted ? null : text, deleted, now));
        while (edits.size() > MAX_PER_PEER) {
            Iterator<Key> eldest = edits.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    /** Lay (va bo khoi bang) lenh dang cho cho tin nay, neu con han. */
    synchronized Optional<Edit> take(String peerId, String messageId, boolean direct) {
        LinkedHashMap<Key, Edit> edits = byPeer.get(peerId);
        if (edits == null) {
            return Optional.empty();
        }
        Edit edit = edits.remove(new Key(messageId, direct));
        if (edits.isEmpty()) {
            byPeer.remove(peerId);
        }
        if (edit == null || clock.getAsLong() - edit.receivedAt() > TTL_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(edit);
    }

    synchronized void forgetPeer(String peerId) {
        byPeer.remove(peerId);
    }

    synchronized int size(String peerId) {
        LinkedHashMap<Key, Edit> edits = byPeer.get(peerId);
        return edits == null ? 0 : edits.size();
    }

    private static void pruneExpired(LinkedHashMap<Key, Edit> edits, long now) {
        edits.values().removeIf(edit -> now - edit.receivedAt() > TTL_MILLIS);
    }
}
