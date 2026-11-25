package com.datn.chatp2p.client.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDeliveryTrackerTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void shutdown() {
        scheduler.shutdownNow();
    }

    @Test
    void resendsUntilAcknowledged() throws Exception {
        AtomicInteger resends = new AtomicInteger();
        CountDownLatch twoResends = new CountDownLatch(2);
        MessageDeliveryTracker tracker = new MessageDeliveryTracker(scheduler, 10, 20,
                (peer, id) -> { throw new AssertionError("khong duoc bo cuoc"); });

        tracker.track("bob", "m1", () -> { resends.incrementAndGet(); twoResends.countDown(); });
        assertTrue(twoResends.await(2, TimeUnit.SECONDS));
        tracker.acknowledge("bob", "m1");
        int afterAck = resends.get();
        Thread.sleep(150);

        assertTrue(resends.get() <= afterAck + 1, "Sau ACK khong duoc gui lai nua");
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void reportsGiveUpAfterMaxRetries() throws Exception {
        AtomicInteger resends = new AtomicInteger();
        AtomicReference<String> gaveUp = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        MessageDeliveryTracker tracker = new MessageDeliveryTracker(scheduler, 3, 10,
                (peer, id) -> { gaveUp.set(peer + "/" + id); done.countDown(); });

        tracker.track("bob", "m1", resends::incrementAndGet);

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("bob/m1", gaveUp.get());
        assertEquals(3, resends.get());
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void forgetPeerCancelsPendingWithoutGiveUp() throws Exception {
        MessageDeliveryTracker tracker = new MessageDeliveryTracker(scheduler, 2, 20,
                (peer, id) -> { throw new AssertionError("peer da roi phong, khong bao loi"); });
        tracker.track("bob", "m1", () -> { });
        tracker.forgetPeer("bob");
        Thread.sleep(150);
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void cancelPendingKeepsReceivedIdsSoResendAfterReconnectIsNotShownTwice() throws Exception {
        MessageDeliveryTracker tracker = new MessageDeliveryTracker(scheduler, 2, 20,
                (peer, id) -> { throw new AssertionError("peer da roi phong, khong bao loi"); });
        assertTrue(tracker.markReceived("bob", "m1"));
        tracker.track("bob", "m2", () -> { });

        tracker.cancelPending("bob"); // bob roi phong, co the vao lai voi cung peerId
        Thread.sleep(150);

        assertEquals(0, tracker.pendingCount());
        assertFalse(tracker.markReceived("bob", "m1"), "Tin da nhan truoc khi bob mat mang khong duoc hien lai");
        tracker.forgetPeer("bob"); // bi ngat han thi moi quen
        assertTrue(tracker.markReceived("bob", "m1"));
    }

    @Test
    void duplicateMessagesAreDetectedPerPeer() {
        MessageDeliveryTracker tracker = new MessageDeliveryTracker(scheduler, 1, 1000, (p, i) -> { });

        assertTrue(tracker.markReceived("bob", "m1"));
        assertFalse(tracker.markReceived("bob", "m1"), "Ban gui lai phai bi loai");
        assertTrue(tracker.markReceived("carol", "m1"), "Cung id nhung khac peer la tin khac");
    }
}
