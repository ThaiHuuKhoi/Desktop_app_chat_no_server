package com.datn.chatp2p.client.session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Giao tin nhan tin cay tren {@code P2pDataChannel} (UDP thuan, khong ACK/retry).
 *
 * <p><b>Ben gui</b>: {@link #track} ghi nho tin nhan dang cho ACK va gui lai dinh
 * ky; {@link #acknowledge} huy khi nhan {@code MESSAGE_ACK}; het luot thi bao
 * {@code onGiveUp}. Moi lan gui lai deu ma hoa lai voi IV moi nen ban ma hoa
 * khac nhau - khong bi co che chong replay cua {@code PeerConnection} chan.
 *
 * <p><b>Ben nhan</b>: {@link #markReceived} loai tin nhan trung (do gui lai khi
 * ACK bi mat) theo {@code messageId}, trong 1 cua so gioi han cho tung peer.
 */
final class MessageDeliveryTracker {

    static final int RECEIVED_WINDOW_SIZE = 512;
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(MessageDeliveryTracker.class.getName());

    private final ScheduledExecutorService scheduler;
    private final int maxRetries;
    private final long retryIntervalMillis;
    private final BiConsumer<String, String> onGiveUp;

    private final Map<Key, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> receivedByPeer = new ConcurrentHashMap<>();

    /**
     * @param onGiveUp goi voi (peerId, messageId) khi da gui lai {@code maxRetries}
     *                 lan ma van chua co ACK.
     */
    MessageDeliveryTracker(ScheduledExecutorService scheduler, int maxRetries, long retryIntervalMillis,
                           BiConsumer<String, String> onGiveUp) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.maxRetries = maxRetries;
        this.retryIntervalMillis = retryIntervalMillis;
        this.onGiveUp = Objects.requireNonNull(onGiveUp, "onGiveUp");
    }

    /** Bat dau cho ACK cho tin nhan DA gui lan dau; {@code resend} goi moi lan gui lai. */
    void track(String peerId, String messageId, Runnable resend) {
        Key key = new Key(peerId, messageId);
        AtomicInteger remaining = new AtomicInteger(maxRetries);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (!pending.containsKey(key)) {
                throw new IllegalStateException("da ACK"); // ngung lich lap
            }
            if (remaining.getAndDecrement() <= 0) {
                if (pending.remove(key) != null) {
                    onGiveUp.accept(peerId, messageId);
                }
                throw new IllegalStateException("het luot gui lai"); // ngung lich lap
            }
            int attempt = maxRetries - remaining.get();
            LOG.fine(() -> "Chua co ACK - gui lai lan " + attempt + "/" + maxRetries + " id="
                    + messageId.substring(0, Math.min(8, messageId.length())));
            try {
                resend.run();
            } catch (RuntimeException ignored) {
                // Loi gui tam thoi - van con luot gui lai; peer roi phong thi forgetPeer() huy.
            }
        }, retryIntervalMillis, retryIntervalMillis, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = pending.put(key, task);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /**
     * Nhan duoc ACK - ngung gui lai.
     *
     * @return {@code true} neu tin nay dang cho ACK (lan dau xac nhan), {@code false} neu ACK trung / la.
     */
    boolean acknowledge(String peerId, String messageId) {
        ScheduledFuture<?> task = pending.remove(new Key(peerId, messageId));
        if (task != null) {
            task.cancel(false);
            return true;
        }
        return false;
    }

    /** @return {@code true} neu la lan DAU nhan {@code messageId} tu {@code peerId} (khong phai ban gui lai). */
    boolean markReceived(String peerId, String messageId) {
        Set<String> seen = receivedByPeer.computeIfAbsent(peerId, id -> Collections.synchronizedSet(
                Collections.newSetFromMap(new LinkedHashMap<>(RECEIVED_WINDOW_SIZE + 1, 1.0f, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                        return size() > RECEIVED_WINDOW_SIZE;
                    }
                })));
        return seen.add(messageId);
    }

    /** {@code true} neu da tung nhan {@code messageId} tu {@code peerId} (con trong cua so nho). */
    boolean hasReceived(String peerId, String messageId) {
        Set<String> seen = receivedByPeer.get(peerId);
        return seen != null && seen.contains(messageId);
    }

    /** Peer bi ngat han - huy moi tin nhan dang cho va quen lich su nhan. */
    void forgetPeer(String peerId) {
        cancelPending(peerId);
        receivedByPeer.remove(peerId);
    }

    /**
     * Peer roi phong (co the vao lai voi cung peerId khi app ho tu ket noi lai): chi huy cac lich gui lai
     * dang chay, GIU so "da nhan" - neu khong, ban gui lai cua mot tin da nhan (ACK bi mat luc mat mang) se
     * hien lan nua sau khi ho ket noi lai. So bi gioi han {@link #RECEIVED_WINDOW_SIZE} id moi peer.
     */
    void cancelPending(String peerId) {
        pending.entrySet().removeIf(entry -> {
            if (entry.getKey().peerId().equals(peerId)) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });
    }

    int pendingCount() {
        return pending.size();
    }

    private record Key(String peerId, String messageId) {
    }
}
