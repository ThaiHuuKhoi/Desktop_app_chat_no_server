package com.datn.chatp2p.signaling.limit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hoi quy van de 11 va 13: han muc theo dia chi, xoa khoa het han, chan tong so khoa, gop IPv6 /64. */
class RequestRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }

        @Override
        public long millis() {
            return now.get();
        }
    };

    @Test
    void limitsRequestsPerKeyWithinTheWindow() {
        RequestRateLimiter limiter = new RequestRateLimiter(3, 60_000, 100, clock);
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertFalse(limiter.tryAcquire("1.2.3.4"));

        now.addAndGet(60_001);
        assertTrue(limiter.tryAcquire("1.2.3.4"), "Het cua so thi duoc phep lai");
    }

    @Test
    void idleKeysAreEvictedAndTotalKeysAreBounded() {
        RequestRateLimiter limiter = new RequestRateLimiter(5, 60_000, 10, clock);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire("10.0.0." + i));
        }
        assertFalse(limiter.tryAcquire("10.0.1.1"), "Day khoa ma cac khoa con hoat dong -> tu choi khoa moi");
        assertEquals(10, limiter.trackedKeys());

        now.addAndGet(60_001);
        assertTrue(limiter.tryAcquire("10.0.1.1"), "Khoa het han phai bi xoa de nhuong cho khoa moi");
        assertEquals(1, limiter.trackedKeys(), "Chi con khoa vua dung");
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareOneKey() {
        assertEquals(RequestRateLimiter.networkKey("2001:db8:1:2::1"),
                RequestRateLimiter.networkKey("2001:db8:1:2:ffff:ffff:ffff:ffff"));
        assertFalse(RequestRateLimiter.networkKey("2001:db8:1:2::1")
                .equals(RequestRateLimiter.networkKey("2001:db8:1:3::1")));
        assertEquals("1.2.3.4", RequestRateLimiter.networkKey("1.2.3.4"));

        RequestRateLimiter limiter = new RequestRateLimiter(2, 60_000, 100, clock);
        assertTrue(limiter.tryAcquire(RequestRateLimiter.networkKey("2001:db8::1")));
        assertTrue(limiter.tryAcquire(RequestRateLimiter.networkKey("2001:db8::2")));
        assertFalse(limiter.tryAcquire(RequestRateLimiter.networkKey("2001:db8::3")),
                "Xoay vong dia chi trong cung /64 khong duoc nhan han muc");
    }
}
