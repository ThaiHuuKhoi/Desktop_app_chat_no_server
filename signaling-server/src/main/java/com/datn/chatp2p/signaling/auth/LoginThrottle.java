package com.datn.chatp2p.signaling.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gioi han thu sai mat khau THEO TAI KHOAN - lop thu hai, sau gioi han theo IP o {@link AuthController}.
 * Gioi han theo IP khong du: ke tan cong co nhieu dia chi (vd. 1 VPS IPv6 thuong co ca dai /48, tuc
 * ~65 nghin dai /64) se co bay nhieu suat thu cho cung 1 tai khoan.
 *
 * <p>Quy tac: {@value #FREE_FAILURES} lan sai lien tiep dau tien khong bi gi; tu lan sai tiep theo, tai
 * khoan bi khoa tam {@link #FIRST_LOCK}, moi lan sai them thi gap doi, toi da {@link #MAX_LOCK}. Dang
 * nhap dung thi xoa chuoi sai. Trong luc bi khoa, ca mat khau dung cung bi tu choi (neu khong thi khoa
 * vo nghia) va server khong chay BCrypt (do ton CPU).
 *
 * <p>Danh doi da chap nhan: ke gian co the co y go sai de khoa tam tai khoan cua nguoi khac, toi da
 * {@link #MAX_LOCK} moi lan. Chi theo doi tai khoan CO THAT (khoa la id) nen bo nho bi chan boi so tai
 * khoan, nguoi la go ten ngau nhien khong lam phinh duoc.
 */
final class LoginThrottle {

    static final int FREE_FAILURES = 5;
    static final Duration FIRST_LOCK = Duration.ofSeconds(30);
    static final Duration MAX_LOCK = Duration.ofMinutes(15);
    /** Lan sai cu hon khoang nay (va khong con bi khoa) thi quen di - go nham tuan truoc khong tinh hom nay. */
    static final Duration FORGET_AFTER = Duration.ofHours(24);

    private final Clock clock;
    private final Map<Long, State> byUser = new ConcurrentHashMap<>();

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Thoi gian con bi khoa; {@link Duration#ZERO} neu duoc thu. */
    Duration remainingLock(long userId) {
        State state = byUser.get(userId);
        if (state == null) {
            return Duration.ZERO;
        }
        synchronized (state) {
            long remaining = state.lockedUntil - clock.millis();
            return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
        }
    }

    void recordFailure(long userId) {
        long now = clock.millis();
        State state = byUser.computeIfAbsent(userId, ignored -> new State());
        synchronized (state) {
            if (state.lockedUntil <= now && now - state.lastFailure > FORGET_AFTER.toMillis()) {
                state.failures = 0;
            }
            state.failures++;
            state.lastFailure = now;
            if (state.failures > FREE_FAILURES) {
                state.lockedUntil = now + lockFor(state.failures).toMillis();
            }
        }
    }

    void recordSuccess(long userId) {
        byUser.remove(userId);
    }

    /** Khoa sau lan sai thu {@code failures}: 30 s, 60 s, 120 s ... toi da 15 phut. */
    static Duration lockFor(int failures) {
        int doublings = Math.min(failures - FREE_FAILURES - 1, 10);
        Duration lock = FIRST_LOCK.multipliedBy(1L << Math.max(0, doublings));
        return lock.compareTo(MAX_LOCK) > 0 ? MAX_LOCK : lock;
    }

    private static final class State {
        int failures;
        long lastFailure;
        long lockedUntil;
    }
}
