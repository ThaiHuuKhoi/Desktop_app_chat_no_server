package com.datn.chatp2p.signaling.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    /** Dong ho chinh tay duoc - de test khoa het han ma khong phai cho that. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-23T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void firstFailuresAreFree() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES; i++) {
            throttle.recordFailure(1);
        }
        assertEquals(Duration.ZERO, throttle.remainingLock(1));
    }

    @Test
    void locksAfterFreeFailuresAndDoublesUpToCap() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES + 1; i++) {
            throttle.recordFailure(1);
        }
        assertEquals(LoginThrottle.FIRST_LOCK, throttle.remainingLock(1));

        assertEquals(Duration.ofSeconds(30), LoginThrottle.lockFor(6));
        assertEquals(Duration.ofSeconds(60), LoginThrottle.lockFor(7));
        assertEquals(Duration.ofSeconds(120), LoginThrottle.lockFor(8));
        assertEquals(LoginThrottle.MAX_LOCK, LoginThrottle.lockFor(20));
        assertEquals(LoginThrottle.MAX_LOCK, LoginThrottle.lockFor(10_000), "Khong tran so khi sai rat nhieu lan");
    }

    @Test
    void lockExpiresWithTime() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES + 1; i++) {
            throttle.recordFailure(1);
        }
        clock.advance(LoginThrottle.FIRST_LOCK);
        assertEquals(Duration.ZERO, throttle.remainingLock(1));

        throttle.recordFailure(1);
        assertEquals(Duration.ofSeconds(60), throttle.remainingLock(1), "Sai tiep sau khi het khoa -> khoa dai gap doi");
    }

    @Test
    void successClearsHistoryAndAccountsAreIndependent() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES + 1; i++) {
            throttle.recordFailure(1);
        }
        assertEquals(Duration.ZERO, throttle.remainingLock(2), "Tai khoan khac khong bi anh huong");

        clock.advance(LoginThrottle.FIRST_LOCK);
        throttle.recordSuccess(1);
        throttle.recordFailure(1);
        assertEquals(Duration.ZERO, throttle.remainingLock(1), "Dang nhap dung thi chuoi sai ve 0");
    }

    @Test
    void oldFailuresAreForgotten() {
        for (int i = 0; i < LoginThrottle.FREE_FAILURES; i++) {
            throttle.recordFailure(1);
        }
        clock.advance(LoginThrottle.FORGET_AFTER.plusSeconds(1));
        throttle.recordFailure(1);
        assertTrue(throttle.remainingLock(1).isZero(), "Go sai tu hom qua khong cong vao hom nay");
    }
}
