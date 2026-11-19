package com.datn.chatp2p.client.view;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Phia NHAN cua "dang go": nho ai dang go o dau, tu het han sau {@link #EXPIRY_MILLIS} neu
 * khong duoc nhac lai (goi {@code typing = false} co the bi mat). Khong thread-safe - chi
 * goi tu JavaFX thread.
 */
final class TypingTracker {

    static final long EXPIRY_MILLIS = 6_000;

    private record Key(String peerId, boolean direct) {
    }

    private final LongSupplier clock;
    /** Thu tu chen = thu tu bat dau go, de cau "A va B dang go" on dinh. */
    private final Map<Key, Long> lastSeen = new LinkedHashMap<>();

    TypingTracker(LongSupplier clock) {
        this.clock = clock;
    }

    void update(String peerId, boolean direct, boolean typing) {
        Key key = new Key(peerId, direct);
        if (typing) {
            lastSeen.putIfAbsent(key, 0L);
            lastSeen.put(key, clock.getAsLong());
        } else {
            lastSeen.remove(key);
        }
    }

    /** Peer vua gui tin o kenh nay - chac chan da go xong. */
    void clear(String peerId, boolean direct) {
        lastSeen.remove(new Key(peerId, direct));
    }

    void removePeer(String peerId) {
        lastSeen.keySet().removeIf(key -> key.peerId().equals(peerId));
    }

    /** Nguoi dang go trong khung nhom. */
    List<String> groupTypers() {
        return active(false, null);
    }

    /** {@code peerId} co dang go trong tab tin nhan rieng voi minh khong. */
    boolean isTypingDirect(String peerId) {
        return !active(true, peerId).isEmpty();
    }

    private List<String> active(boolean direct, String onlyPeerId) {
        long now = clock.getAsLong();
        List<String> result = new ArrayList<>();
        Iterator<Map.Entry<Key, Long>> iterator = lastSeen.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Long> entry = iterator.next();
            if (now - entry.getValue() >= EXPIRY_MILLIS) {
                iterator.remove();
                continue;
            }
            Key key = entry.getKey();
            if (key.direct() == direct && (onlyPeerId == null || onlyPeerId.equals(key.peerId()))) {
                result.add(key.peerId());
            }
        }
        return result;
    }

    /** Cau hien thi: "" / "An đang gõ…" / "An và Bình đang gõ…" / "An, Bình và 2 người khác đang gõ…". */
    static String describe(List<String> names) {
        return switch (names.size()) {
            case 0 -> "";
            case 1 -> names.get(0) + " đang gõ…";
            case 2 -> names.get(0) + " và " + names.get(1) + " đang gõ…";
            default -> names.get(0) + ", " + names.get(1) + " và " + (names.size() - 2) + " người khác đang gõ…";
        };
    }
}
