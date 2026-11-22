package com.datn.chatp2p.signaling.limit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gioi han so yeu cau trong 1 cua so truot theo tung khoa (thuong la dia chi IP), dung chung cho
 * {@code GET /turn-credentials} va {@code POST /auth/*}.
 *
 * <p>Khac ban cu (map khong bao gio xoa khoa): khoa het yeu cau trong cua so bi xoa, va tong so khoa
 * bi chan tren {@code maxKeys} - nguoi la xoay vong rat nhieu dia chi khong lam phinh bo nho server.
 * Khi da day ma van con khoa dang hoat dong, yeu cau tu khoa MOI bi tu choi (fail closed).
 *
 * <p>Dia chi IPv6 duoc gop theo /64 ({@link #networkKey}): mot may thuong co ca dai /64 nen khoa theo
 * tung /128 thi han muc moi IP vo nghia.
 */
public final class RequestRateLimiter {

    public static final int DEFAULT_MAX_KEYS = 10_000;

    private final int maxRequests;
    private final long windowMillis;
    private final int maxKeys;
    private final Clock clock;
    private final Map<String, Deque<Long>> requestsByKey = new ConcurrentHashMap<>();

    public RequestRateLimiter(int maxRequests, long windowMillis) {
        this(maxRequests, windowMillis, DEFAULT_MAX_KEYS, Clock.systemUTC());
    }

    public RequestRateLimiter(int maxRequests, long windowMillis, int maxKeys, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.maxKeys = maxKeys;
        this.clock = clock;
    }

    /** Ghi nhan 1 yeu cau cua {@code key}; tra ve false neu vuot han muc (khong ghi nhan). */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (!requestsByKey.containsKey(key) && requestsByKey.size() >= maxKeys) {
            evictIdle(now);
            if (requestsByKey.size() >= maxKeys) {
                return false;
            }
        }
        Deque<Long> times = requestsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, now);
            if (times.size() >= maxRequests) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** So khoa dang duoc theo doi (cho test va giam sat). */
    public int trackedKeys() {
        return requestsByKey.size();
    }

    /** Xoa moi khoa khong con yeu cau nao trong cua so. */
    public void evictIdle() {
        evictIdle(clock.millis());
    }

    private void evictIdle(long now) {
        requestsByKey.forEach((key, times) -> {
            synchronized (times) {
                prune(times, now);
                if (times.isEmpty()) {
                    requestsByKey.remove(key, times);
                }
            }
        });
    }

    private void prune(Deque<Long> times, long now) {
        long cutoff = now - windowMillis;
        while (!times.isEmpty() && times.peekFirst() <= cutoff) {
            times.removeFirst();
        }
    }

    /**
     * Khoa gioi han cho 1 dia chi: IPv4 giu nguyen, IPv6 cat con /64 (8 byte dau). Chuoi khong phai IP
     * literal thi tra ve nguyen van (khong bao gio tra cuu DNS).
     */
    public static String networkKey(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        if (address.indexOf(':') < 0) {
            return address;
        }
        try {
            String literal = address.startsWith("[") && address.endsWith("]")
                    ? address.substring(1, address.length() - 1) : address;
            int zone = literal.indexOf('%');
            if (zone >= 0) {
                literal = literal.substring(0, zone);
            }
            byte[] bytes = InetAddress.getByName(literal).getAddress();
            if (bytes.length != 16) {
                return address;
            }
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) {
                prefix.append(String.format("%02x", bytes[i] & 0xff));
            }
            return prefix.append("/64").toString();
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
