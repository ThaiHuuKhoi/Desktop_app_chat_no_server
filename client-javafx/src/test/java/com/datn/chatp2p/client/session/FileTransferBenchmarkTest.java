package com.datn.chatp2p.client.session;

import com.datn.chatp2p.client.session.FileTransferStatus.State;
import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.filetransfer.FileTransferLimits;
import com.datn.chatp2p.p2p.filetransfer.ReliableTransferConfig;
import com.datn.chatp2p.p2p.ice.IceP2pConnectionEstablisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Do toc do truyen file tren duong truyen THAT cua ung dung: 2 {@link IceP2pConnectionEstablisher}
 * tren localhost (socket ice4j that, ca hang doi nhan cua no) -&gt; {@code P2pDataChannel} (chia
 * manh) -&gt; {@link PeerConnection} (ECDH + AES-GCM) -&gt; {@link FileTransferManager}.
 *
 * <p>Mat goi gia lap THEO DATAGRAM: 1 ban tin chia thanh n manh bi mat voi xac suat 1-(1-p)^n
 * (mat 1 manh la mat ca ban tin, dung nhu P2pDataChannel).
 *
 * <p>Khong chay mac dinh (lau, phu thuoc may). Chay:
 * {@code mvn -pl client-javafx test -Dtest=FileTransferBenchmarkTest -Dchatp2p.benchmark=true}
 */
class FileTransferBenchmarkTest {

    private static final int FILE_BYTES = 4 * 1024 * 1024;
    /** Phai khop P2pDataChannel.MAX_FRAGMENT_DATA. */
    private static final int FRAGMENT_DATA = 1150;

    @TempDir
    Path temp;

    @Test
    void benchmark() throws Exception {
        assumeTrue(Boolean.getBoolean("chatp2p.benchmark"), "dat -Dchatp2p.benchmark=true de chay");
        Path file = temp.resolve("payload.bin");
        byte[] data = new byte[FILE_BYTES];
        new Random(7).nextBytes(data);
        Files.write(file, data);

        String filter = System.getProperty("chatp2p.benchmark.cases", "");
        int repeats = Integer.getInteger("chatp2p.benchmark.repeats", 3);
        int[][] cases = {
                // chunk, window
                {512, 64},
                {2048, 32},
                {4096, 8}, {4096, 16},
                {8192, 8},
        };
        double[] losses = java.util.Arrays.stream(System.getProperty("chatp2p.benchmark.losses", "0,1,3,5")
                .split(",")).mapToDouble(s -> Double.parseDouble(s) / 100).toArray();
        run(file, data, 4096, 16, 0.0, 0); // khoi dong JIT, khong tinh
        System.out.println("BENCH | chunk  | window | loss | median MB/s | min..max MB/s | runs");
        for (double loss : losses) {
            for (int[] c : cases) {
                String key = c[0] + "/" + c[1];
                if (!filter.isEmpty() && !List.of(filter.split(",")).contains(key)) {
                    continue;
                }
                List<Result> results = new java.util.ArrayList<>();
                for (int i = 0; i < repeats; i++) {
                    results.add(run(file, data, c[0], c[1], loss, i)); // moi lan 1 kieu mat goi khac
                }
                double[] mbps = results.stream().mapToDouble(Result::mbps).sorted().toArray();
                String outcomes = String.join(",", results.stream().map(Result::outcome).toList());
                System.out.printf("BENCH | %6d | %6d | %3.0f%% | %11.2f | %5.2f..%5.2f | %s%n",
                        c[0], c[1], loss * 100, mbps[mbps.length / 2], mbps[0], mbps[mbps.length - 1], outcomes);
            }
        }
    }

    private record Result(long millis, String outcome) {
        double mbps() {
            return "OK".equals(outcome) ? FILE_BYTES / 1_048_576.0 / (millis / 1000.0) : 0;
        }
    }

    private Result run(Path file, byte[] expected, int chunk, int window, double loss, int attempt) throws Exception {
        IceP2pConnectionEstablisher a = new IceP2pConnectionEstablisher(List.of());
        IceP2pConnectionEstablisher b = new IceP2pConnectionEstablisher(List.of());
        FileTransferManager alice = null;
        FileTransferManager bob = null;
        try {
            CompletableFuture<DataChannel> aChannel = new CompletableFuture<>();
            CompletableFuture<DataChannel> bChannel = new CompletableFuture<>();
            a.onConnected(aChannel::complete);
            b.onConnected(bChannel::complete);
            a.onFailed(aChannel::completeExceptionally);
            b.onFailed(bChannel::completeExceptionally);
            a.acceptAnswer(b.createAnswer(a.createOffer()));

            Lossy aLossy = new Lossy(aChannel.get(20, TimeUnit.SECONDS), loss, 11 + 1000L * attempt);
            Lossy bLossy = new Lossy(bChannel.get(20, TimeUnit.SECONDS), loss, 12 + 1000L * attempt);

            AtomicReference<FileTransferManager> aliceRef = new AtomicReference<>();
            AtomicReference<FileTransferManager> bobRef = new AtomicReference<>();
            CountDownLatch handshakes = new CountDownLatch(2);
            PeerConnection aConn = new PeerConnection("bob", aLossy, KeyExchangeService.generateKeyPair(),
                    (from, env) -> aliceRef.get().handleEnvelope("bob", env), handshakes::countDown);
            PeerConnection bConn = new PeerConnection("alice", bLossy, KeyExchangeService.generateKeyPair(),
                    (from, env) -> bobRef.get().handleEnvelope("alice", env), handshakes::countDown);

            FileTransferLimits limits = new FileTransferLimits(FileTransferLimits.DEFAULT_MAX_FILE_SIZE, chunk);
            ReliableTransferConfig config = new ReliableTransferConfig(window,
                    ReliableTransferConfig.DEFAULT.initialRetryMillis(), ReliableTransferConfig.DEFAULT.maxRetryMillis(),
                    ReliableTransferConfig.DEFAULT.maxRetries(), ReliableTransferConfig.DEFAULT.offerRetryMillis(),
                    ReliableTransferConfig.DEFAULT.answerTimeoutMillis());
            Path downloads = Files.createDirectories(
                    temp.resolve("dl-" + chunk + "-" + window + "-" + loss + "-" + attempt + "-" + System.nanoTime()));

            CompletableFuture<FileTransferStatus> aliceDone = new CompletableFuture<>();
            CompletableFuture<FileTransferStatus> bobDone = new CompletableFuture<>();
            alice = new FileTransferManager((peer, type, ns, payload) -> aConn.send(type, ns, payload),
                    finalState(aliceDone), limits, config, FileTransferManager.RECEIVE_IDLE_TIMEOUT_MS);
            FileTransferManager[] bobHolder = new FileTransferManager[1];
            Consumer<FileTransferStatus> bobListener = status -> {
                if (status.state() == State.OFFERED) {
                    bobHolder[0].acceptFile(status.fileId(), downloads);
                }
                finalState(bobDone).accept(status);
            };
            bob = new FileTransferManager((peer, type, ns, payload) -> bConn.send(type, ns, payload),
                    bobListener, limits, config, FileTransferManager.RECEIVE_IDLE_TIMEOUT_MS);
            bobHolder[0] = bob;
            aliceRef.set(alice);
            bobRef.set(bob);

            aConn.sendEcdhPublicKey();
            bConn.sendEcdhPublicKey();
            assertTrue(handshakes.await(10, TimeUnit.SECONDS), "handshake ECDH");
            aLossy.enabled = true;
            bLossy.enabled = true;

            long start = System.nanoTime();
            alice.sendFile("bob", file, false);
            FileTransferStatus sent;
            FileTransferStatus received;
            try {
                sent = aliceDone.get(180, TimeUnit.SECONDS);
                received = bobDone.get(10, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                return new Result((System.nanoTime() - start) / 1_000_000, "HANG");
            }
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (sent.state() != State.COMPLETED || received.state() != State.COMPLETED) {
                return new Result(millis, sent.state() + "/" + sent.reason());
            }
            assertArrayEquals(expected, Files.readAllBytes(received.savedFile()));
            aConn.close();
            bConn.close();
            return new Result(millis, "OK");
        } finally {
            if (alice != null) alice.close();
            if (bob != null) bob.close();
            a.dispose();
            b.dispose();
        }
    }

    private static Consumer<FileTransferStatus> finalState(CompletableFuture<FileTransferStatus> done) {
        return status -> {
            if (status.state().isFinal()) {
                done.complete(status);
            }
        };
    }

    /** Bo ca ban tin voi xac suat mat it nhat 1 trong n datagram cua no. */
    private static final class Lossy implements DataChannel {
        private final DataChannel inner;
        private final double loss;
        private final Random random;
        volatile boolean enabled;

        Lossy(DataChannel inner, double loss, long seed) {
            this.inner = inner;
            this.loss = loss;
            this.random = new Random(seed);
        }

        @Override
        public void send(byte[] data) {
            if (enabled && loss > 0) {
                int datagrams = data.length + 1 <= FRAGMENT_DATA + 9 ? 1 : (data.length + FRAGMENT_DATA - 1) / FRAGMENT_DATA;
                double dropProbability = 1 - Math.pow(1 - loss, datagrams);
                synchronized (random) {
                    if (random.nextDouble() < dropProbability) {
                        return;
                    }
                }
            }
            inner.send(data);
        }

        @Override
        public void onReceive(Consumer<byte[]> handler) {
            inner.onReceive(handler);
        }

        @Override
        public void close() {
            inner.close();
        }
    }
}
