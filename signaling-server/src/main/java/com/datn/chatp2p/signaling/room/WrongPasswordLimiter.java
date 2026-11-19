package com.datn.chatp2p.signaling.room;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gioi han so lan JOIN sai mat khau theo dia chi IP (Tai-lieu-ky-thuat.md muc J.2): toi da
 * {@code maxFailures} lan trong {@code windowMillis}. Vi server bao "sai mat khau" ngay, khong co
 * gioi han thi nguoi ngoai co the dung server de do mat khau tu dong.
 *
 * <p>Tinh theo IP chu khong theo ket noi: dong WebSocket roi mo lai la qua duoc gioi han theo ket noi.
 */
public final class WrongPasswordLimiter {

    public static final int DEFAULT_MAX_FAILURES = 5;
    public static final long DEFAULT_WINDOW_MILLIS = 60_000;

    private final int maxFailures;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> failuresByKey = new ConcurrentHashMap<>();

    public WrongPasswordLimiter() {
        this(DEFAULT_MAX_FAILURES, DEFAULT_WINDOW_MILLIS, Clock.systemUTC());
    }

    public WrongPasswordLimiter(int maxFailures, long windowMillis, Clock clock) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** {@code true} neu {@code key} da sai du so lan cho phep trong cua so thoi gian hien tai. */
    public boolean isBlocked(String key) {
        Deque<Long> failures = failuresByKey.get(key);
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            prune(failures);
            if (failures.isEmpty()) {
                failuresByKey.remove(key, failures);
                return false;
            }
            return failures.size() >= maxFailures;
        }
    }

    /** Ghi nhan 1 lan sai mat khau cua {@code key}. */
    public void recordFailure(String key) {
        Deque<Long> failures = failuresByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (failures) {
            prune(failures);
            failures.addLast(clock.millis());
        }
    }

    private void prune(Deque<Long> failures) {
        long cutoff = clock.millis() - windowMillis;
        while (!failures.isEmpty() && failures.peekFirst() <= cutoff) {
            failures.removeFirst();
        }
    }
}
