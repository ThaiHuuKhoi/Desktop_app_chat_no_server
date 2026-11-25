package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.MessagePayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tin nhan minh da gui ma chua co {@code MESSAGE_ACK}, giu lai de gui lai khi ket noi voi dung nguoi do
 * duoc thiet lap lai (vd ho mat Wi-Fi vai phut, app ho tu ket noi lai va vao lai phong voi cung peerId).
 *
 * <p>Truoc day {@link MessageDeliveryTracker} thu lai 6 x 0,5 s roi bo cuoc: tin hien "Chua toi noi" va khong
 * bao gio duoc gui lai, ke ca khi da ket noi lai (phat hien khi test 2 may 25/11).
 *
 * <p><b>Bao mat:</b> peerId do client tu dat, nen sau khi nguoi cu roi phong, ke khac co the vao lai bang
 * dung peerId do. Vi vay chi gui lai khi nguoi ket noi lai co <b>cung van tay danh tinh</b> (da xac thuc
 * chu ky {@code PEER_IDENTITY}) voi luc gui tin - tin rieng khong bi gui nham cho ke mao danh. Tin gui luc
 * chua xac thuc duoc danh tinh nguoi nhan (van tay null) thi khong bao gio gui lai.
 *
 * <p>Gioi han {@link #MAX_PER_PEER} tin moi nguoi (bo tin cu nhat) de bo nho khong phinh. Thread-safe.
 */
final class UndeliveredMessages {

    static final int MAX_PER_PEER = 200;

    /** Tin cho gui lai: namespace (null = nhom, "dm" = rieng), noi dung, van tay nguoi nhan luc gui. */
    record Entry(String namespace, MessagePayload payload, String recipientFingerprint) {
    }

    private final Map<String, LinkedHashMap<String, Entry>> byPeer = new HashMap<>();

    /** Ghi nho tin vua gui toi {@code peerId}; {@code recipientFingerprint} null neu chua xac thuc danh tinh. */
    synchronized void remember(String peerId, String namespace, MessagePayload payload, String recipientFingerprint) {
        LinkedHashMap<String, Entry> pending = byPeer.computeIfAbsent(peerId, id -> new LinkedHashMap<>());
        pending.put(payload.id(), new Entry(namespace, payload, recipientFingerprint));
        Iterator<String> oldest = pending.keySet().iterator();
        while (pending.size() > MAX_PER_PEER && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    /** Nhan ACK - khong can gui lai nua. */
    synchronized void acknowledge(String peerId, String messageId) {
        LinkedHashMap<String, Entry> pending = byPeer.get(peerId);
        if (pending != null) {
            pending.remove(messageId);
            if (pending.isEmpty()) {
                byPeer.remove(peerId);
            }
        }
    }

    /**
     * Cac tin can gui lai cho {@code peerId} vua ket noi lai va da xac thuc danh tinh voi van tay
     * {@code fingerprint}, theo thu tu gui. Tin van duoc giu toi khi co ACK. Tin gui cho mot danh tinh khac
     * (cung peerId nhung khac van tay) bi bo han - khong bao gio gui cho nguoi nay.
     */
    synchronized List<Entry> forReconnected(String peerId, String fingerprint) {
        LinkedHashMap<String, Entry> pending = byPeer.get(peerId);
        if (pending == null || fingerprint == null) {
            return List.of();
        }
        List<Entry> resend = new ArrayList<>();
        pending.values().removeIf(entry -> !fingerprint.equals(entry.recipientFingerprint()));
        resend.addAll(pending.values());
        if (pending.isEmpty()) {
            byPeer.remove(peerId);
        }
        return resend;
    }

    /** Bo moi tin cho gui lai toi {@code peerId} (vd nguoi do bi ngat vi sai bang chung mat khau). */
    synchronized void forget(String peerId) {
        byPeer.remove(peerId);
    }

    synchronized int count(String peerId) {
        LinkedHashMap<String, Entry> pending = byPeer.get(peerId);
        return pending == null ? 0 : pending.size();
    }
}
