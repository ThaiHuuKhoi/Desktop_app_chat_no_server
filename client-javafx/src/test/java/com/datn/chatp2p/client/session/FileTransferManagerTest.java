package com.datn.chatp2p.client.session;

import com.datn.chatp2p.client.session.FileTransferStatus.Direction;
import com.datn.chatp2p.client.session.FileTransferStatus.State;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.p2p.filetransfer.FileTransferLimits;
import com.datn.chatp2p.p2p.filetransfer.ReliableTransferConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 2 {@link FileTransferManager} ("alice" gui, "bob" nhan) noi voi nhau qua 1
 * "mang" trong bo nho: payload di qua JSON that, moi ben nhan tren 1 luong rieng
 * (giong vong nhan cua P2pDataChannel), co the lam mat goi FILE_CHUNK / FILE_ACK.
 */
class FileTransferManagerTest {

    private static final long WAIT_MS = 20_000;

    @TempDir
    Path temp;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService aliceInbox = Executors.newSingleThreadExecutor();
    private final ExecutorService bobInbox = Executors.newSingleThreadExecutor();
    private final Random random = new Random(42);

    /** Ti le mat goi FILE_CHUNK / FILE_ACK (0..1). */
    private volatile double lossRate;
    /** Bo het FILE_CHUNK - de giu lan truyen "dang chay" khi test huy / timeout. */
    private volatile boolean dropAllChunks;
    /** So FILE_ANSWER dau tien bi mat. */
    private final AtomicInteger answersToDrop = new AtomicInteger();
    /** Moi ban tin da gui: "nguoiGui:TYPE". */
    private final List<String> sent = new CopyOnWriteArrayList<>();

    private final Recorder aliceEvents = new Recorder();
    private final Recorder bobEvents = new Recorder();
    private FileTransferManager alice;
    private FileTransferManager bob;

    private void connect(FileTransferLimits bobLimits, long answerTimeoutMs, long idleTimeoutMs) {
        // Tham so cua A nhung nhanh hon de test khong cho lau: gui lai offer moi 100 ms
        ReliableTransferConfig config = new ReliableTransferConfig(32, 100, 800, 10, 100, answerTimeoutMs);
        alice = new FileTransferManager(network(() -> bob, aliceInbox, "alice", bobInbox), aliceEvents,
                FileTransferLimits.DEFAULT, config, idleTimeoutMs);
        bob = new FileTransferManager(network(() -> alice, bobInbox, "bob", aliceInbox), bobEvents,
                bobLimits, config, idleTimeoutMs);
    }

    private void connect() {
        connect(FileTransferLimits.DEFAULT, 60_000, 30_000);
    }

    private FileTransferManager.Transport network(java.util.function.Supplier<FileTransferManager> other,
                                                  ExecutorService ownInbox, String fromPeerId,
                                                  ExecutorService otherInbox) {
        return (peerId, type, namespace, payload) -> {
            sent.add(fromPeerId + ":" + type);
            if (type == EnvelopeType.FILE_CHUNK && dropAllChunks) {
                return;
            }
            if (type == EnvelopeType.FILE_ANSWER && answersToDrop.getAndDecrement() > 0) {
                return;
            }
            if ((type == EnvelopeType.FILE_CHUNK || type == EnvelopeType.FILE_ACK) && lossRate > 0) {
                synchronized (random) {
                    if (random.nextDouble() < lossRate) {
                        return;
                    }
                }
            }
            byte[] json;
            try {
                json = mapper.writeValueAsBytes(payload);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            Envelope envelope = new Envelope(type, namespace, System.currentTimeMillis(), json);
            otherInbox.execute(() -> other.get().handleEnvelope(fromPeerId, envelope));
        };
    }

    @AfterEach
    void tearDown() {
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
        aliceInbox.shutdownNow();
        bobInbox.shutdownNow();
    }

    private Path source(String name, int size) throws Exception {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        Path dir = Files.createDirectories(temp.resolve("source"));
        return Files.write(dir.resolve(name), data);
    }

    private Path downloads() {
        return temp.resolve("downloads");
    }

    /** Bob tu dong bam "Nhan" moi loi moi. */
    private void bobAcceptsEverything() {
        bobEvents.onEach = status -> {
            if (status.state() == State.OFFERED) {
                bob.acceptFile(status.fileId(), downloads());
            }
        };
    }

    // ------------------------------------------------------------------ thanh cong

    @Test
    void fileArrivesIntactAndBothSidesComplete() throws Exception {
        connect();
        bobAcceptsEverything();
        Path file = source("bao cao.pdf", 300_000);

        FileTransferStatus sent = alice.sendFile("bob", file, false);

        FileTransferStatus received = bobEvents.await(s -> s.state() == State.COMPLETED);
        aliceEvents.await(s -> s.state() == State.COMPLETED);
        assertEquals(Direction.INCOMING, received.direction());
        assertEquals(sent.fileId(), received.fileId());
        assertEquals("alice", received.peerId());
        assertEquals(downloads().resolve("bao cao.pdf"), received.savedFile());
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(received.savedFile()));
    }

    @Test
    void fileSurvivesLossOfChunksAndAcks() throws Exception {
        connect();
        bobAcceptsEverything();
        lossRate = 0.15;
        Path file = source("lossy.bin", 200_000);

        alice.sendFile("bob", file, false);

        FileTransferStatus received = bobEvents.await(s -> s.state() == State.COMPLETED);
        aliceEvents.await(s -> s.state() == State.COMPLETED);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(received.savedFile()));
    }

    @Test
    void emptyFileIsTransferred() throws Exception {
        connect();
        bobAcceptsEverything();
        Path file = source("rong.txt", 0);

        alice.sendFile("bob", file, true);

        FileTransferStatus received = bobEvents.await(s -> s.state() == State.COMPLETED);
        aliceEvents.await(s -> s.state() == State.COMPLETED);
        assertTrue(received.direct());
        assertEquals(0, Files.size(received.savedFile()));
    }

    @Test
    void progressIsReportedWhileSending() throws Exception {
        connect();
        bobAcceptsEverything();

        alice.sendFile("bob", source("lon.bin", 1024 * 1024), false);

        aliceEvents.await(s -> s.state() == State.COMPLETED);
        assertTrue(aliceEvents.all.stream().anyMatch(s -> s.state() == State.WAITING_ANSWER));
        assertTrue(aliceEvents.all.stream().anyMatch(s -> s.state() == State.IN_PROGRESS));
    }

    // ------------------------------------------------------------------ tu choi / huy

    @Test
    void rejectedOfferIsReportedToSender() throws Exception {
        connect();
        bobEvents.onEach = status -> {
            if (status.state() == State.OFFERED) {
                bob.rejectFile(status.fileId());
            }
        };

        alice.sendFile("bob", source("a.bin", 1000), false);

        FileTransferStatus rejected = aliceEvents.await(s -> s.state() == State.REJECTED);
        assertEquals("REJECTED", rejected.reason());
        assertFalse(Files.exists(downloads()));
    }

    @Test
    void tooLargeFileIsRejectedAutomatically() throws Exception {
        connect(new FileTransferLimits(500, 4096), 60_000, 30_000);

        alice.sendFile("bob", source("a.bin", 1000), false);

        FileTransferStatus rejected = aliceEvents.await(s -> s.state() == State.REJECTED);
        assertEquals("FILE_TOO_LARGE", rejected.reason());
        assertEquals("FILE_TOO_LARGE", bobEvents.await(s -> s.state() == State.REJECTED).reason());
    }

    @Test
    void senderCancelRemovesPartFileOnReceiver() throws Exception {
        connect();
        bobAcceptsEverything();
        dropAllChunks = true;

        FileTransferStatus sent = alice.sendFile("bob", source("a.bin", 100_000), false);
        aliceEvents.await(s -> s.state() == State.IN_PROGRESS);
        bobEvents.await(s -> s.state() == State.IN_PROGRESS);

        alice.cancelFile(sent.fileId());

        assertEquals("CANCELLED_BY_USER", aliceEvents.await(s -> s.state() == State.CANCELLED).reason());
        assertEquals(FileTransferStatus.CANCELLED_BY_PEER,
                bobEvents.await(s -> s.state() == State.CANCELLED).reason());
        assertEquals(List.of(), filesIn(downloads()));
    }

    @Test
    void receiverCancelStopsSender() throws Exception {
        connect();
        bobAcceptsEverything();
        dropAllChunks = true;

        alice.sendFile("bob", source("a.bin", 100_000), false);
        FileTransferStatus receiving = bobEvents.await(s -> s.state() == State.IN_PROGRESS);

        bob.cancelFile(receiving.fileId());

        assertEquals(FileTransferStatus.CANCELLED_BY_PEER,
                aliceEvents.await(s -> s.state() == State.CANCELLED).reason());
        assertEquals(List.of(), filesIn(downloads()));
    }

    // ------------------------------------------------------------------ het gio / mat ket noi

    @Test
    void unansweredOfferTimesOut() throws Exception {
        connect(FileTransferLimits.DEFAULT, 300, 30_000);

        alice.sendFile("bob", source("a.bin", 1000), false);

        assertEquals("TIMEOUT", aliceEvents.await(s -> s.state() == State.FAILED).reason());
        // Bob dang thay loi moi -> nhan FILE_CANCEL TIMEOUT -> the cua bob cung ket thuc
        assertEquals("TIMEOUT", bobEvents.await(s -> s.state() == State.FAILED).reason());
    }

    @Test
    void receiverGivesUpWhenChunksStopArriving() throws Exception {
        connect(FileTransferLimits.DEFAULT, 60_000, 400);
        bobAcceptsEverything();
        dropAllChunks = true;

        alice.sendFile("bob", source("a.bin", 100_000), false);

        assertEquals("TIMEOUT", bobEvents.await(s -> s.state() == State.FAILED).reason());
        assertEquals("TIMEOUT", aliceEvents.await(s -> s.state() == State.FAILED).reason());
        assertEquals(List.of(), filesIn(downloads()));
    }

    @Test
    void peerLeavingStopsTransfer() throws Exception {
        connect();
        bobAcceptsEverything();
        dropAllChunks = true;

        alice.sendFile("bob", source("a.bin", 100_000), false);
        aliceEvents.await(s -> s.state() == State.IN_PROGRESS);

        alice.onPeerLeft("bob");

        assertEquals(FileTransferStatus.PEER_LEFT, aliceEvents.await(s -> s.state() == State.FAILED).reason());
    }

    @Test
    void chunksFromAnotherPeerAreIgnored() throws Exception {
        connect();
        bobAcceptsEverything();
        dropAllChunks = true;

        FileTransferStatus sent = alice.sendFile("bob", source("a.bin", 5000), false);
        bobEvents.await(s -> s.state() == State.IN_PROGRESS);

        // "mallory" gui chunk mang fileId cua alice
        byte[] json = mapper.writeValueAsBytes(new com.datn.chatp2p.common.protocol.FileChunkPayload(
                sent.fileId(), 0, new byte[4096]));
        bob.handleEnvelope("mallory", new Envelope(EnvelopeType.FILE_CHUNK, null, 0, json));
        bobInbox.submit(() -> { }).get();

        assertTrue(bobEvents.all.stream().noneMatch(s -> s.bytesTransferred() > 0));
    }

    // ------------------------------------------------------------------ ghep voi phan cua A

    @Test
    void lostAnswerIsRecoveredByOfferRetransmission() throws Exception {
        connect();
        bobAcceptsEverything();
        answersToDrop.set(1); // FILE_ANSWER dau tien cua bob bi mat

        Path file = source("a.bin", 20_000);
        alice.sendFile("bob", file, false);

        FileTransferStatus received = bobEvents.await(s -> s.state() == State.COMPLETED);
        aliceEvents.await(s -> s.state() == State.COMPLETED);
        assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(received.savedFile()));
        // alice gui lai offer, bob tra loi lai (khong hoi lai nguoi dung)
        assertTrue(sent.stream().filter("alice:FILE_OFFER"::equals).count() >= 2);
        assertEquals(1, bobEvents.all.stream().filter(s -> s.state() == State.OFFERED).count());
    }

    @Test
    void secondOfferFromSamePeerIsBusyWhileReceiving() throws Exception {
        connect();
        bobAcceptsEverything();
        dropAllChunks = true;

        alice.sendFile("bob", source("a.bin", 50_000), false);
        bobEvents.await(s -> s.state() == State.IN_PROGRESS);

        FileTransferStatus second = alice.sendFile("bob", source("b.bin", 1000), false);

        FileTransferStatus busy = aliceEvents.await(s -> s.fileId().equals(second.fileId())
                && s.state() == State.REJECTED);
        assertEquals("BUSY", busy.reason());
    }

    @Test
    void chunkOfUnknownFileIsAnsweredWithCancel() throws Exception {
        connect();

        byte[] json = mapper.writeValueAsBytes(new com.datn.chatp2p.common.protocol.FileChunkPayload(
                "khong-ton-tai", 0, new byte[10]));
        bob.handleEnvelope("alice", new Envelope(EnvelopeType.FILE_CHUNK, null, 0, json));

        assertTrue(sent.contains("bob:FILE_CANCEL"));
    }

    @Test
    void lateChunkAfterCompletionGetsVerifiedAckAgain() throws Exception {
        connect();
        bobAcceptsEverything();

        FileTransferStatus sentStatus = alice.sendFile("bob", source("a.bin", 5000), false);
        bobEvents.await(s -> s.state() == State.COMPLETED);
        aliceEvents.await(s -> s.state() == State.COMPLETED);
        long acksBefore = sent.stream().filter("bob:FILE_ACK"::equals).count();

        byte[] json = mapper.writeValueAsBytes(new com.datn.chatp2p.common.protocol.FileChunkPayload(
                sentStatus.fileId(), 1, new byte[5000 - 4096]));
        bob.handleEnvelope("alice", new Envelope(EnvelopeType.FILE_CHUNK, null, 0, json));

        assertEquals(acksBefore + 1, sent.stream().filter("bob:FILE_ACK"::equals).count());
    }

    // ------------------------------------------------------------------ tien ich

    private static List<Path> filesIn(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        }
    }

    private static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }

    /** Ghi lai moi trang thai 1 ben nhan duoc; cho toi khi co trang thai thoa dieu kien. */
    private static final class Recorder implements java.util.function.Consumer<FileTransferStatus> {
        final List<FileTransferStatus> all = new CopyOnWriteArrayList<>();
        final Map<String, FileTransferStatus> latest = new ConcurrentHashMap<>();
        volatile java.util.function.Consumer<FileTransferStatus> onEach = status -> { };

        @Override
        public void accept(FileTransferStatus status) {
            all.add(status);
            latest.put(status.fileId(), status);
            onEach.accept(status);
        }

        FileTransferStatus await(Predicate<FileTransferStatus> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                for (FileTransferStatus status : all) {
                    if (condition.test(status)) {
                        return status;
                    }
                }
                Thread.sleep(20);
            }
            fail("Khong thay trang thai mong doi. Da nhan: " + all);
            return null;
        }
    }
}
