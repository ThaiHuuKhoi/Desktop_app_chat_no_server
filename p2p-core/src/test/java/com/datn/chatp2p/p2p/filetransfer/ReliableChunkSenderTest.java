package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.common.protocol.FileTransferReason;
import com.datn.chatp2p.p2p.NetworkImpairment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test tang gui tin cay (A) voi {@link ChunkSource} gia + ben nhan toi thieu, qua duong truyen
 * gia lap mat / nhan doi / dao goi ({@link NetworkImpairment}) - khong phu thuoc phan cua B.
 */
class ReliableChunkSenderTest {

    /** Tham so rut gon cho test chay nhanh (mang gia lap tre toi da vai chuc ms). */
    private static final ReliableTransferConfig FAST =
            new ReliableTransferConfig(32, 100, 800, 10, 100, 3_000);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @AfterEach
    void shutdown() {
        scheduler.shutdownNow();
    }

    /** Noi sender <-> receiver qua 2 duong truyen (xuoi: offer/chunk, nguoc: answer/ack/cancel). */
    private final class Harness implements ReliableTransferListener {
        final NetworkImpairment forward;
        final NetworkImpairment backward;
        final InMemoryChunkSource source;
        final TestFileReceiver receiver;
        ReliableChunkSender sender;

        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicInteger callbacks = new AtomicInteger();
        final Set<Integer> sentIndexes = ConcurrentHashMap.newKeySet();
        final AtomicInteger chunkSends = new AtomicInteger();
        volatile boolean completed;
        volatile String failure;
        volatile int lastAcked;

        Harness(InMemoryChunkSource source, NetworkImpairment forward, NetworkImpairment backward,
                ReliableTransferConfig config, boolean accept) {
            this.source = source;
            this.forward = forward;
            this.backward = backward;
            this.receiver = new TestFileReceiver(
                    answer -> backward.deliver(answer, a -> sender.onAnswer(a)),
                    ack -> backward.deliver(ack, a -> sender.onAck(a)),
                    cancel -> backward.deliver(cancel, c -> sender.onCancel(c)),
                    scheduler, accept);
            this.sender = new ReliableChunkSender(source,
                    offer -> forward.deliver(offer, receiver::onOffer),
                    chunk -> {
                        sentIndexes.add(chunk.index());
                        chunkSends.incrementAndGet();
                        forward.deliver(chunk, receiver::onChunk);
                    },
                    this, scheduler, config);
        }

        Harness start() {
            sender.start();
            return this;
        }

        boolean await(long seconds) throws InterruptedException {
            return finished.await(seconds, TimeUnit.SECONDS);
        }

        @Override
        public void onProgress(int ackedChunks, int totalChunks) {
            lastAcked = Math.max(lastAcked, ackedChunks);
        }

        @Override
        public void onCompleted() {
            completed = true;
            callbacks.incrementAndGet();
            finished.countDown();
        }

        @Override
        public void onFailed(String reason) {
            failure = reason;
            callbacks.incrementAndGet();
            finished.countDown();
        }
    }

    private NetworkImpairment perfect() {
        return NetworkImpairment.perfect(scheduler);
    }

    @Test
    void transfersFileOverPerfectNetwork() throws Exception {
        InMemoryChunkSource source = InMemoryChunkSource.random(200_000, 4096, 1);
        Harness h = new Harness(source, perfect(), perfect(), FAST, true).start();

        assertTrue(h.await(10), "Phai hoan tat trong 10s");
        assertTrue(h.completed, "That bai: " + h.failure);
        assertArrayEquals(source.data(), h.receiver.result());
        assertEquals(source.offer().totalChunks(), h.lastAcked);
        assertEquals(1, h.callbacks.get(), "onCompleted/onFailed chi duoc goi dung 1 lan");
    }

    @Test
    void transfersFileOverLossyDuplicatingReorderingNetwork() throws Exception {
        InMemoryChunkSource source = InMemoryChunkSource.random(512 * 1024 + 123, 4096, 2);
        NetworkImpairment forward = new NetworkImpairment(scheduler, 11, 0.20, 0.10, 30);
        NetworkImpairment backward = new NetworkImpairment(scheduler, 12, 0.20, 0.10, 30);
        Harness h = new Harness(source, forward, backward, FAST, true).start();

        assertTrue(h.await(60), "Phai hoan tat trong 60s du mat 20% goi");
        assertTrue(h.completed, "That bai: " + h.failure);
        assertArrayEquals(source.data(), h.receiver.result(), "Du lieu nhan phai dung tung byte");
        assertEquals(0, h.receiver.rejectedChunks());
        assertTrue(forward.droppedCount() > 0 && forward.duplicatedCount() > 0,
                "Kich ban phai thuc su co mat va nhan doi goi");
    }

    @Test
    void completesWhenFinalVerifiedAckIsLost() throws Exception {
        InMemoryChunkSource source = InMemoryChunkSource.random(40_000, 4096, 3);
        NetworkImpairment backward = perfect();
        AtomicBoolean droppedOnce = new AtomicBoolean();
        backward.setForceDrop(message -> message instanceof FileAckPayload ack
                && ack.verified() && droppedOnce.compareAndSet(false, true));
        Harness h = new Harness(source, perfect(), backward, FAST, true).start();

        assertTrue(h.await(10));
        assertTrue(h.completed, "Mat ACK verified cuoi cung khong duoc lam treo giao dich: " + h.failure);
        assertTrue(droppedOnce.get(), "Kich ban phai thuc su lam mat ACK verified");
    }

    @Test
    void reportsRejectionFromReceiver() throws Exception {
        Harness h = new Harness(InMemoryChunkSource.random(10_000, 4096, 4),
                perfect(), perfect(), FAST, false).start();

        assertTrue(h.await(5));
        assertEquals(FileTransferReason.REJECTED, h.failure);
        assertEquals(0, h.chunkSends.get(), "Bi tu choi thi khong gui chunk nao");
    }

    @Test
    void retransmitsOfferAndTimesOutWhenNoAnswer() throws Exception {
        NetworkImpairment forward = perfect();
        forward.blackHole();
        ReliableTransferConfig config = new ReliableTransferConfig(32, 100, 800, 10, 100, 500);
        Harness h = new Harness(InMemoryChunkSource.random(10_000, 4096, 5),
                forward, perfect(), config, true).start();

        assertTrue(h.await(5));
        assertEquals(FileTransferReason.TIMEOUT, h.failure);
        assertTrue(forward.sentCount() >= 3, "Offer phai duoc gui lai, thuc te gui " + forward.sentCount() + " lan");
    }

    @Test
    void neverExceedsWindowAndTimesOutWhenChunksNeverArrive() throws Exception {
        NetworkImpairment forward = perfect();
        forward.setForceDrop(message -> message instanceof FileChunkPayload);
        ReliableTransferConfig config = new ReliableTransferConfig(8, 20, 80, 3, 100, 3_000);
        Harness h = new Harness(InMemoryChunkSource.random(50 * 4096, 4096, 6),
                forward, perfect(), config, true).start();

        assertTrue(h.await(10));
        assertEquals(FileTransferReason.TIMEOUT, h.failure);
        assertEquals(8, h.sentIndexes.size(), "Khong co ACK thi chi duoc gui dung 1 cua so chunk");
    }

    @Test
    void emptyFileCompletesRightAfterAcceptWithoutChunks() throws Exception {
        Harness h = new Harness(new InMemoryChunkSource(new byte[0], 4096),
                perfect(), perfect(), FAST, true).start();

        assertTrue(h.await(5));
        assertTrue(h.completed, "That bai: " + h.failure);
        assertEquals(0, h.chunkSends.get());
    }

    @Test
    void reportsChecksumMismatchCancelFromReceiver() throws Exception {
        byte[] data = new byte[20_000];
        InMemoryChunkSource lying = new InMemoryChunkSource(data, 4096, "0".repeat(64));
        Harness h = new Harness(lying, perfect(), perfect(), FAST, true).start();

        assertTrue(h.await(5));
        assertEquals(FileTransferReason.CHECKSUM_MISMATCH, h.failure);
        assertNull(h.receiver.result());
    }

    @Test
    void localCancelStopsAllSendingWithoutCallback() throws Exception {
        NetworkImpairment forward = perfect();
        forward.setForceDrop(message -> message instanceof FileChunkPayload);
        Harness h = new Harness(InMemoryChunkSource.random(50 * 4096, 4096, 7),
                forward, perfect(), FAST, true).start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (h.chunkSends.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(h.chunkSends.get() > 0, "Phai bat dau gui chunk sau khi duoc chap nhan");

        h.sender.cancel();
        int sendsAtCancel = h.chunkSends.get();
        Thread.sleep(400);

        assertEquals(sendsAtCancel, h.chunkSends.get(), "Sau cancel() khong duoc gui lai chunk nao");
        assertEquals(0, h.callbacks.get(), "cancel() tu phia minh khong goi listener");
        assertTrue(h.sender.isFinished());
    }

    @Test
    void rejectsOfferThatBreaksAgreedConventions() {
        ChunkSource tooBigChunks = new InMemoryChunkSource(new byte[100_000], 64 * 1024);
        assertThrows(IllegalArgumentException.class, () -> new ReliableChunkSender(tooBigChunks,
                o -> { }, c -> { }, new Harness(InMemoryChunkSource.random(1, 1, 0), perfect(), perfect(), FAST, true),
                scheduler));

        FileOfferPayload wrongCount = new FileOfferPayload("id", "a", 10_000, "x", 4096, 2, "0".repeat(64));
        assertEquals("totalChunks khong khop ceil(fileSize / chunkSize)", FileTransferLimits.validate(wrongCount));
    }
}
