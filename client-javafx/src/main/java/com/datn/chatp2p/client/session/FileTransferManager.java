package com.datn.chatp2p.client.session;

import com.datn.chatp2p.client.session.FileTransferStatus.Direction;
import com.datn.chatp2p.client.session.FileTransferStatus.State;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileAnswerPayload;
import com.datn.chatp2p.common.protocol.FileCancelPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.common.protocol.FileTransferReason;
import com.datn.chatp2p.p2p.filetransfer.FileNames;
import com.datn.chatp2p.p2p.filetransfer.FileReceiver;
import com.datn.chatp2p.p2p.filetransfer.FileReceiver.ChunkResult;
import com.datn.chatp2p.p2p.filetransfer.FileSender;
import com.datn.chatp2p.p2p.filetransfer.FileTransferException;
import com.datn.chatp2p.p2p.filetransfer.FileTransferLimits;
import com.datn.chatp2p.p2p.filetransfer.ReliableChunkSender;
import com.datn.chatp2p.p2p.filetransfer.ReliableTransferConfig;
import com.datn.chatp2p.p2p.filetransfer.ReliableTransferListener;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dieu phoi giao thuc truyen file 1-1 (docs/Thong-nhat-giao-thuc-truyen-file.md +
 * docs/Tra-loi-giao-thuc-truyen-file.md cua A).
 *
 * <ul>
 *   <li><b>Ben gui</b>: {@link FileSender} (B) doc file, {@link ReliableChunkSender} (A) gui
 *       FILE_OFFER (tu gui lai toi khi co tra loi), gui chunk theo cua so truot, gui lai chunk
 *       mat, cho ACK cuoi {@code verified}. Lop nay chuyen FILE_ANSWER / FILE_ACK / FILE_CANCEL
 *       vao sender va gui FILE_CANCEL khi loi phia minh hoac nguoi dung bam Huy.</li>
 *   <li><b>Ben nhan</b>: {@link FileReceiver} (B) ghi chunk, gui FILE_ACK (moi 8 chunk moi,
 *       khi co lo hong, hoac sau 50 ms khong co chunk moi), kiem SHA-256 roi gui ACK
 *       {@code verified}. Theo checklist cua A: offer trung -> gui lai cau tra loi cu; chunk cua
 *       file da xong -> gui lai ACK {@code verified}; chunk cua file la / da huy -> FILE_CANCEL;
 *       dang nhan file khac tu cung peer -> {@code BUSY}.</li>
 * </ul>
 *
 * <p>Khong phu thuoc JavaFX hay {@code RoomSession}: gui qua {@link Transport}, bao trang thai
 * qua {@code Consumer<FileTransferStatus>} -> test duoc bang 2 manager noi voi nhau trong bo nho.
 *
 * <p>Nguyen tac khoa: moi lan truyen co khoa rieng ({@code Outgoing} / {@code Incoming});
 * KHONG goi {@link ReliableChunkSender} khi dang giu khoa do (sender goi nguoc listener).
 */
final class FileTransferManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FileTransferManager.class.getName());

    /** Gui 1 payload toi dung 1 peer. */
    interface Transport {
        void send(String peerId, EnvelopeType type, String namespace, Object payload);
    }

    /**
     * Kich thuoc chunk khi gui qua mang that: 512 byte.
     *
     * <p>LY DO (da do that): {@code MergingDatagramSocket} cua ice4j nhan moi goi UDP vao bo dem
     * CO DINH 1500 byte - goi lon hon bi cat cut va {@code P2pDataChannel} am tham bo di. Chunk
     * 4 KB (D5) sau EnvelopeCodec thanh ~7,3 KB nen KHONG BAO GIO toi noi. Chunk 512 byte ->
     * goi ~1,1 KB, vua 1 goi &lt;= 1200 byte (an toan ca voi MTU duong Internet / TURN). Doi lai
     * {@link FileTransferLimits#DEFAULT_CHUNK_SIZE} khi {@code P2pDataChannel} chia nho goi.</p>
     */
    static final int NETWORK_CHUNK_SIZE = 512;

    static final FileTransferLimits NETWORK_LIMITS =
            new FileTransferLimits(FileTransferLimits.DEFAULT_MAX_FILE_SIZE, NETWORK_CHUNK_SIZE);

    /**
     * Tham so D5 cua A, rieng cua so 64 chunk (64 x 512 byte = 32 KB dang bay) de bu chunk nho.
     * Khong tang qua ~90: hang doi nhan cua ice4j chi giu 100 goi.
     */
    static final ReliableTransferConfig NETWORK_CONFIG = new ReliableTransferConfig(64,
            ReliableTransferConfig.DEFAULT.initialRetryMillis(), ReliableTransferConfig.DEFAULT.maxRetryMillis(),
            ReliableTransferConfig.DEFAULT.maxRetries(), ReliableTransferConfig.DEFAULT.offerRetryMillis(),
            ReliableTransferConfig.DEFAULT.answerTimeoutMillis());

    static final long RECEIVE_IDLE_TIMEOUT_MS = 30_000;
    /** Gui ACK sau moi bao nhieu chunk moi nhan theo thu tu. */
    static final int ACK_EVERY_CHUNKS = 8;
    /** Gui ACK neu sau chung nay ms khong co chunk moi (A, muc 2.4). */
    static final long ACK_DELAY_MS = 50;
    /** D4: toi da 64 phan tu trong receivedAbove. */
    static final int ACK_SELECTIVE_LIMIT = 64;
    /** Giu trang thai file da ket thuc de tra loi offer / chunk den muon (A, lo hong 1-2). */
    static final long CLOSED_RETENTION_MS = 30_000;
    private static final long PROGRESS_INTERVAL_MS = 100;
    /** Ly do tu choi tu FILE_ANSWER (khac voi loi giua chung). */
    private static final Set<String> ANSWER_REJECT_REASONS = Set.of(FileTransferReason.REJECTED,
            FileTransferReason.FILE_TOO_LARGE, FileTransferReason.INVALID_OFFER, FileTransferReason.BUSY);

    private final Transport transport;
    private final Consumer<FileTransferStatus> listener;
    private final FileTransferLimits limits;
    private final ReliableTransferConfig senderConfig;
    private final long receiveIdleTimeoutMs;
    private final ScheduledExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Incoming> incoming = new ConcurrentHashMap<>();
    /** File nhan da ket thuc (xong / tu choi / huy) -> cach tra loi offer / chunk den muon. */
    private final Map<String, ClosedIncoming> closed = new ConcurrentHashMap<>();
    private volatile boolean shutDown;

    FileTransferManager(Transport transport, Consumer<FileTransferStatus> listener) {
        this(transport, listener, NETWORK_LIMITS, NETWORK_CONFIG, RECEIVE_IDLE_TIMEOUT_MS);
    }

    FileTransferManager(Transport transport, Consumer<FileTransferStatus> listener,
                        FileTransferLimits limits, ReliableTransferConfig senderConfig,
                        long receiveIdleTimeoutMs) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.senderConfig = Objects.requireNonNull(senderConfig, "senderConfig");
        this.receiveIdleTimeoutMs = receiveIdleTimeoutMs;
        this.executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "file-transfer");
            thread.setDaemon(true);
            return thread;
        });
        long watchdogPeriod = Math.max(100, Math.min(5_000, receiveIdleTimeoutMs / 3));
        executor.scheduleWithFixedDelay(this::watchdog, watchdogPeriod, watchdogPeriod, TimeUnit.MILLISECONDS);
    }

    FileTransferLimits limits() {
        return limits;
    }

    // ================================================================ ben gui

    /**
     * Doc + bam SHA-256 file roi bat dau gui (FILE_OFFER). Ham nay CHAN (doc ca file) -
     * khong goi tu luong giao dien.
     *
     * @throws FileTransferException    {@code FILE_TOO_LARGE} / {@code IO_ERROR}
     * @throws IllegalArgumentException neu {@code file} khong phai file thuong
     */
    FileTransferStatus sendFile(String peerId, Path file, boolean direct) {
        Objects.requireNonNull(peerId, "peerId");
        if (shutDown) {
            throw new IllegalStateException("Da roi phong");
        }
        FileSender source = FileSender.open(file, limits);
        FileOfferPayload offer = source.offer();
        Outgoing out = new Outgoing(peerId, source, new FileTransferStatus(
                offer.fileId(), peerId, Direction.OUTGOING, direct, offer.fileName(), offer.fileSize(),
                State.WAITING_ANSWER, 0, null, null));
        try {
            out.sender = new ReliableChunkSender(source,
                    // namespace chi gan vao OFFER de ben nhan biet hien the o tab Nhom hay DM
                    sentOffer -> transport.send(peerId, EnvelopeType.FILE_OFFER, namespace(direct), sentOffer),
                    chunk -> transport.send(peerId, EnvelopeType.FILE_CHUNK, null, chunk),
                    new SenderListener(out), executor, senderConfig);
        } catch (RuntimeException e) {
            source.close();
            throw e;
        }
        outgoing.put(offer.fileId(), out);
        emit(out.status);
        out.sender.start();
        synchronized (out) {
            return out.status;
        }
    }

    private void onAnswer(String peerId, FileAnswerPayload answer) {
        Outgoing out = outgoing.get(answer.fileId());
        if (out == null || !out.peerId.equals(peerId)) {
            return;
        }
        FileTransferStatus accepted = null;
        synchronized (out) {
            if (out.status.state() == State.WAITING_ANSWER) {
                if (answer.accepted()) {
                    out.status = out.status.with(State.IN_PROGRESS, 0, null, null);
                    accepted = out.status;
                } else {
                    out.rejected = true;
                }
            }
        }
        if (accepted != null) {
            emit(accepted);
        }
        out.sender.onAnswer(answer); // NGOAI khoa; tu choi -> listener.onFailed(reason)
    }

    private void onAck(String peerId, FileAckPayload ack) {
        Outgoing out = outgoing.get(ack.fileId());
        if (out != null && out.peerId.equals(peerId)) {
            out.sender.onAck(ack);
        }
    }

    /** Nhan su kien tu {@link ReliableChunkSender} cua 1 file (goi ngoai khoa cua sender). */
    private final class SenderListener implements ReliableTransferListener {
        private final Outgoing out;

        SenderListener(Outgoing out) {
            this.out = out;
        }

        @Override
        public void onProgress(int ackedChunks, int totalChunks) {
            FileTransferStatus status;
            synchronized (out) {
                if (out.status.state() != State.IN_PROGRESS) {
                    return;
                }
                long bytes = Math.min((long) ackedChunks * out.source.offer().chunkSize(), out.status.fileSize());
                out.status = out.status.with(State.IN_PROGRESS, bytes, null, null);
                long now = System.currentTimeMillis();
                if (now - out.lastEmitAt < PROGRESS_INTERVAL_MS && ackedChunks < totalChunks) {
                    return;
                }
                out.lastEmitAt = now;
                status = out.status;
            }
            emit(status);
        }

        @Override
        public void onCompleted() {
            finishOutgoing(out, State.COMPLETED, null, false);
        }

        @Override
        public void onFailed(String reason) {
            boolean rejected;
            boolean byPeer;
            synchronized (out) {
                rejected = out.rejected;
                byPeer = out.cancelledByPeer;
            }
            if (rejected) {
                // FILE_ANSWER accepted=false: tu choi (REJECTED/BUSY/...) hoac ben kia khong tao duoc file
                finishOutgoing(out, ANSWER_REJECT_REASONS.contains(reason) ? State.REJECTED : State.FAILED,
                        reason, false);
            } else if (byPeer) {
                // Ben kia gui FILE_CANCEL - khong gui lai
                boolean userCancel = FileTransferReason.CANCELLED_BY_USER.equals(reason);
                finishOutgoing(out, userCancel ? State.CANCELLED : State.FAILED,
                        userCancel ? FileTransferStatus.CANCELLED_BY_PEER : reason, false);
            } else {
                // Loi phia minh (TIMEOUT, IO_ERROR): sender KHONG tu gui FILE_CANCEL -> minh gui
                finishOutgoing(out, State.FAILED, reason, true);
            }
        }
    }

    /**
     * Ket thuc 1 lan gui: dung sender, dong file, bao giao dien.
     *
     * @param notifyPeer gui FILE_CANCEL(reason) cho ben kia
     */
    private void finishOutgoing(Outgoing out, State state, String reason, boolean notifyPeer) {
        FileTransferStatus status;
        synchronized (out) {
            if (out.status.state().isFinal()) {
                return;
            }
            long bytes = state == State.COMPLETED ? out.status.fileSize() : out.status.bytesTransferred();
            out.status = out.status.with(state, bytes, null, reason);
            status = out.status;
        }
        outgoing.remove(status.fileId(), out);
        out.sender.cancel(); // khong goi listener; khong lam gi neu sender da ket thuc
        out.source.close();
        if (notifyPeer) {
            trySend(out.peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(status.fileId(), reason));
        }
        emit(status);
    }

    // ================================================================ ben nhan

    private void onOffer(String peerId, String namespace, FileOfferPayload offer) {
        if (offer.fileId() == null) {
            return;
        }
        // Offer trung (ben gui gui lai vi chua thay tra loi): gui lai dung cau tra loi cu
        Incoming existing = incoming.get(offer.fileId());
        if (existing != null) {
            FileAnswerPayload previous;
            synchronized (existing) {
                previous = existing.answer; // null = nguoi dung chua bam gi -> im lang
            }
            if (previous != null && existing.peerId.equals(peerId)) {
                trySend(peerId, EnvelopeType.FILE_ANSWER, null, previous);
            }
            return;
        }
        ClosedIncoming done = closed.get(offer.fileId());
        if (done != null) {
            if (done.peerId.equals(peerId) && done.answer != null) {
                trySend(peerId, EnvelopeType.FILE_ANSWER, null, done.answer);
            }
            return;
        }

        boolean direct = EnvelopeNamespace.DIRECT_MESSAGE.equals(namespace);
        FileTransferStatus offered = new FileTransferStatus(
                offer.fileId(), peerId, Direction.INCOMING, direct, FileNames.sanitize(offer.fileName()),
                Math.max(0, offer.fileSize()), State.OFFERED, 0, null, null);

        String autoReject = null;
        try {
            FileReceiver.validateOffer(offer, limits);
        } catch (FileTransferException e) {
            autoReject = e.reason().name();
        }
        if (autoReject == null && isBusyWith(peerId)) {
            autoReject = FileTransferReason.BUSY;
        }
        if (autoReject != null) {
            // Tu choi ngay, khong hoi nguoi dung (offer sai / qua lon / dang nhan file khac)
            FileAnswerPayload answer = new FileAnswerPayload(offer.fileId(), false, autoReject);
            remember(offer.fileId(), new ClosedIncoming(peerId, answer, null, null));
            trySend(peerId, EnvelopeType.FILE_ANSWER, null, answer);
            emit(offered.with(State.REJECTED, 0, null, autoReject));
            return;
        }
        Incoming in = new Incoming(peerId, offer, offered);
        if (incoming.putIfAbsent(offer.fileId(), in) == null) {
            emit(offered);
        }
    }

    /** Dang nhan (da bam Nhan) 1 file khac tu cung peer -> offer moi bi {@code BUSY}. */
    private boolean isBusyWith(String peerId) {
        for (Incoming in : incoming.values()) {
            if (in.peerId.equals(peerId)) {
                synchronized (in) {
                    State state = in.status.state();
                    if (state == State.IN_PROGRESS || state == State.VERIFYING) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Nguoi dung bam "Nhan": tao file tam trong {@code directory} va tra loi FILE_ANSWER. */
    void acceptFile(String fileId, Path directory) {
        Incoming in = incoming.get(fileId);
        if (in == null) {
            return;
        }
        FileTransferStatus status;
        FileAnswerPayload answer;
        boolean completeNow = false;
        synchronized (in) {
            if (in.status.state() != State.OFFERED) {
                return;
            }
            try {
                in.receiver = FileReceiver.accept(in.offer, directory, limits);
                in.lastChunkAt = System.currentTimeMillis();
                completeNow = in.receiver.isComplete(); // file rong: khong co chunk nao
                in.status = in.status.with(completeNow ? State.VERIFYING : State.IN_PROGRESS, 0, null, null);
                answer = new FileAnswerPayload(fileId, true, null);
                in.answer = answer;
            } catch (FileTransferException e) {
                // Khong tao duoc file tam (vd thu muc khong ghi duoc) -> bao ben gui
                String reason = e.reason().name();
                answer = new FileAnswerPayload(fileId, false, reason);
                in.status = in.status.with(State.FAILED, 0, null, reason);
                incoming.remove(fileId, in);
                remember(fileId, new ClosedIncoming(in.peerId, answer, null, null));
            }
            status = in.status;
        }
        // Gui SAU khi da san sang nhan chunk
        trySend(in.peerId, EnvelopeType.FILE_ANSWER, null, answer);
        emit(status);
        if (completeNow) {
            executor.execute(() -> completeIncoming(in));
        }
    }

    /** Nguoi dung bam "Tu choi". */
    void rejectFile(String fileId) {
        Incoming in = incoming.get(fileId);
        if (in == null) {
            return;
        }
        FileTransferStatus status;
        FileAnswerPayload answer = new FileAnswerPayload(fileId, false, FileTransferReason.REJECTED);
        synchronized (in) {
            if (in.status.state() != State.OFFERED) {
                return;
            }
            in.status = in.status.with(State.REJECTED, 0, null, FileTransferReason.REJECTED);
            in.answer = answer;
            status = in.status;
        }
        incoming.remove(fileId, in);
        remember(fileId, new ClosedIncoming(in.peerId, answer, null, null));
        trySend(in.peerId, EnvelopeType.FILE_ANSWER, null, answer);
        emit(status);
    }

    private void onChunk(String peerId, FileChunkPayload chunk) {
        Incoming in = incoming.get(chunk.fileId());
        if (in == null || !in.peerId.equals(peerId)) {
            answerStrayChunk(peerId, chunk);
            return;
        }
        FileAckPayload ack = null;
        FileTransferStatus progress = null;
        boolean completeNow = false;
        synchronized (in) {
            if (in.receiver == null || in.status.state() != State.IN_PROGRESS) {
                return; // chua bam Nhan, hoac dang kiem SHA-256
            }
            ChunkResult result;
            try {
                result = in.receiver.onChunk(chunk);
            } catch (FileTransferException e) {
                // Ghi dia that bai - receiver da tu huy va xoa file tam
                String reason = e.reason().name();
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, reason);
                FileTransferStatus failed = in.status;
                incoming.remove(chunk.fileId(), in);
                remember(chunk.fileId(), new ClosedIncoming(peerId, in.answer, null, reason));
                trySend(peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(chunk.fileId(), reason));
                emit(failed);
                return;
            }
            if (result == ChunkResult.REJECTED) {
                return;
            }
            in.lastChunkAt = System.currentTimeMillis();
            if (result == ChunkResult.ACCEPTED) {
                in.chunksSinceAck++;
                long bytes = Math.min((long) in.receiver.receivedChunks() * in.offer.chunkSize(), in.offer.fileSize());
                in.status = in.status.with(State.IN_PROGRESS, bytes, null, null);
            }
            if (in.receiver.isComplete()) {
                cancelTimer(in.delayedAck);
                in.status = in.status.with(State.VERIFYING, in.offer.fileSize(), null, null);
                progress = in.status;
                completeNow = true;
            } else {
                List<Integer> above = in.receiver.receivedAbove(ACK_SELECTIVE_LIMIT);
                if (result == ChunkResult.DUPLICATE || !above.isEmpty() || in.chunksSinceAck >= ACK_EVERY_CHUNKS) {
                    ack = ackNow(in, above);
                } else if (in.delayedAck == null || in.delayedAck.isDone()) {
                    // Chua du 8 chunk: van ACK neu 50 ms khong co chunk moi (vd cuoi cua so)
                    in.delayedAck = executor.schedule(() -> sendDelayedAck(in), ACK_DELAY_MS, TimeUnit.MILLISECONDS);
                }
                long now = System.currentTimeMillis();
                if (now - in.lastEmitAt >= PROGRESS_INTERVAL_MS) {
                    in.lastEmitAt = now;
                    progress = in.status;
                }
            }
        }
        if (ack != null) {
            trySend(peerId, EnvelopeType.FILE_ACK, null, ack);
        }
        if (progress != null) {
            emit(progress);
        }
        if (completeNow) {
            executor.execute(() -> completeIncoming(in));
        }
    }

    /** Tao FILE_ACK hien tai va reset bo dem. Goi trong khoa cua {@code in}. */
    private FileAckPayload ackNow(Incoming in, List<Integer> above) {
        cancelTimer(in.delayedAck);
        in.chunksSinceAck = 0;
        return new FileAckPayload(in.offer.fileId(), in.receiver.contiguousChunks(), above, false);
    }

    private void sendDelayedAck(Incoming in) {
        FileAckPayload ack;
        synchronized (in) {
            if (in.status.state() != State.IN_PROGRESS || in.chunksSinceAck == 0) {
                return;
            }
            ack = ackNow(in, in.receiver.receivedAbove(ACK_SELECTIVE_LIMIT));
        }
        trySend(in.peerId, EnvelopeType.FILE_ACK, null, ack);
    }

    /**
     * Chunk cua file khong con dang nhan: da xong -> gui lai ACK {@code verified} (mat ACK cuoi);
     * da huy / la -> FILE_CANCEL de ben gui dung (ke ca khi ban CANCEL truoc bi mat).
     */
    private void answerStrayChunk(String peerId, FileChunkPayload chunk) {
        ClosedIncoming done = closed.get(chunk.fileId());
        if (done != null) {
            if (!done.peerId.equals(peerId)) {
                return;
            }
            if (done.finalAck != null) {
                trySend(peerId, EnvelopeType.FILE_ACK, null, done.finalAck);
            } else if (done.cancelReason != null) {
                trySend(peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(chunk.fileId(), done.cancelReason));
            }
            return;
        }
        if (!incoming.containsKey(chunk.fileId())) {
            // Hoan toan khong biet file nay (vd vua mo lai app) -> bao ben gui dung
            trySend(peerId, EnvelopeType.FILE_CANCEL, null,
                    new FileCancelPayload(chunk.fileId(), FileTransferReason.INVALID_OFFER));
        }
    }

    /** Kiem SHA-256 + doi ten (chay tren executor, khong chan luong nhan mang). */
    private void completeIncoming(Incoming in) {
        FileTransferStatus status;
        String fileId = in.offer.fileId();
        FileAckPayload finalAck = null;
        synchronized (in) {
            if (in.status.state() != State.VERIFYING) {
                return;
            }
            try {
                Path saved = in.receiver.complete();
                finalAck = new FileAckPayload(fileId, in.offer.totalChunks(), List.of(), true);
                in.status = in.status.with(State.COMPLETED, in.offer.fileSize(), saved, null);
            } catch (FileTransferException e) {
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, e.reason().name());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Khong hoan tat duoc file " + fileId, e);
                in.receiver.cancel();
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, FileTransferReason.IO_ERROR);
            }
            status = in.status;
        }
        if (status.state() == State.COMPLETED) {
            remember(fileId, new ClosedIncoming(in.peerId, in.answer, finalAck, null));
            incoming.remove(fileId, in);
            trySend(in.peerId, EnvelopeType.FILE_ACK, null, finalAck);
        } else {
            remember(fileId, new ClosedIncoming(in.peerId, in.answer, null, status.reason()));
            incoming.remove(fileId, in);
            trySend(in.peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(fileId, status.reason()));
        }
        emit(status);
    }

    private void finishIncoming(Incoming in, State state, String reason, boolean notifyPeer) {
        FileTransferStatus status;
        synchronized (in) {
            if (in.status.state().isFinal()) {
                return;
            }
            cancelTimer(in.delayedAck);
            if (in.receiver != null) {
                in.receiver.cancel();
            }
            in.status = in.status.with(state, in.status.bytesTransferred(), null, reason);
            status = in.status;
        }
        incoming.remove(status.fileId(), in);
        String cancelReason = FileTransferStatus.CANCELLED_BY_PEER.equals(reason)
                ? FileTransferReason.CANCELLED_BY_USER : reason;
        remember(status.fileId(), new ClosedIncoming(in.peerId, in.answer, null, cancelReason));
        if (notifyPeer) {
            trySend(in.peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(status.fileId(), reason));
        }
        emit(status);
    }

    // ================================================================ chung

    /** Nguoi dung bam "Huy" (gui hoac nhan). Loi moi chua tra loi = tu choi. */
    void cancelFile(String fileId) {
        Outgoing out = outgoing.get(fileId);
        if (out != null) {
            finishOutgoing(out, State.CANCELLED, FileTransferReason.CANCELLED_BY_USER, true);
            return;
        }
        Incoming in = incoming.get(fileId);
        if (in == null) {
            return;
        }
        boolean offered;
        synchronized (in) {
            offered = in.status.state() == State.OFFERED;
        }
        if (offered) {
            rejectFile(fileId);
        } else {
            finishIncoming(in, State.CANCELLED, FileTransferReason.CANCELLED_BY_USER, true);
        }
    }

    /** Xu ly 1 Envelope FILE_* tu {@code peerId}. Payload hong thi bo qua. */
    void handleEnvelope(String peerId, Envelope envelope) {
        if (shutDown || envelope == null || envelope.type() == null) {
            return;
        }
        try {
            switch (envelope.type()) {
                case FILE_OFFER -> onOffer(peerId, envelope.namespace(), parse(envelope, FileOfferPayload.class));
                case FILE_ANSWER -> onAnswer(peerId, parse(envelope, FileAnswerPayload.class));
                case FILE_CHUNK -> onChunk(peerId, parse(envelope, FileChunkPayload.class));
                case FILE_ACK -> onAck(peerId, parse(envelope, FileAckPayload.class));
                case FILE_CANCEL -> onCancel(peerId, parse(envelope, FileCancelPayload.class));
                default -> {
                    // khong phai envelope truyen file
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Payload truyen file khong hop le tu peer " + peerId, e);
        }
    }

    private void onCancel(String peerId, FileCancelPayload cancel) {
        if (cancel.fileId() == null) {
            return;
        }
        Outgoing out = outgoing.get(cancel.fileId());
        if (out != null && out.peerId.equals(peerId)) {
            synchronized (out) {
                out.cancelledByPeer = true;
            }
            out.sender.onCancel(cancel); // -> listener.onFailed(reason)
            return;
        }
        Incoming in = incoming.get(cancel.fileId());
        if (in != null && in.peerId.equals(peerId)) {
            boolean byUser = FileTransferReason.CANCELLED_BY_USER.equals(cancel.reason());
            finishIncoming(in, byUser ? State.CANCELLED : State.FAILED,
                    byUser ? FileTransferStatus.CANCELLED_BY_PEER : cancel.reason(), false);
        }
    }

    /** Peer roi phong / ket noi bi thay the: dung moi lan truyen voi peer do (khong gui gi them). */
    void onPeerLeft(String peerId) {
        for (Outgoing out : outgoing.values()) {
            if (out.peerId.equals(peerId)) {
                finishOutgoing(out, State.FAILED, FileTransferStatus.PEER_LEFT, false);
            }
        }
        for (Incoming in : incoming.values()) {
            if (in.peerId.equals(peerId)) {
                finishIncoming(in, State.FAILED, FileTransferStatus.PEER_LEFT, false);
            }
        }
    }

    /** Roi phong: huy moi lan truyen dang chay, bao ben kia, dung luong nen. */
    @Override
    public void close() {
        if (shutDown) {
            return;
        }
        for (Outgoing out : outgoing.values()) {
            finishOutgoing(out, State.CANCELLED, FileTransferReason.CANCELLED_BY_USER, true);
        }
        for (Incoming in : incoming.values()) {
            boolean offered;
            synchronized (in) {
                offered = in.status.state() == State.OFFERED;
            }
            if (offered) {
                rejectFile(in.offer.fileId());
            } else {
                finishIncoming(in, State.CANCELLED, FileTransferReason.CANCELLED_BY_USER, true);
            }
        }
        shutDown = true;
        executor.shutdownNow();
    }

    /** Dinh ky: huy file nhan khong con chunk toi, don cac file da ket thuc qua 30 s. */
    private void watchdog() {
        long now = System.currentTimeMillis();
        for (Incoming in : incoming.values()) {
            boolean idle;
            synchronized (in) {
                idle = in.status.state() == State.IN_PROGRESS && now - in.lastChunkAt > receiveIdleTimeoutMs;
            }
            if (idle) {
                finishIncoming(in, State.FAILED, FileTransferReason.TIMEOUT, true);
            }
        }
        closed.values().removeIf(done -> done.closedAt + CLOSED_RETENTION_MS < now);
    }

    private void remember(String fileId, ClosedIncoming done) {
        closed.put(fileId, done);
    }

    private <T> T parse(Envelope envelope, Class<T> type) throws IOException {
        T payload = mapper.readValue(envelope.payload(), type);
        if (payload == null) {
            throw new IOException("Payload rong");
        }
        return payload;
    }

    private boolean trySend(String peerId, EnvelopeType type, String namespace, Object payload) {
        try {
            transport.send(peerId, type, namespace, payload);
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.INFO, "Khong gui duoc " + type + " toi " + peerId + ": " + e.getMessage());
            return false;
        }
    }

    private void emit(FileTransferStatus status) {
        try {
            listener.accept(status);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Listener truyen file loi", e);
        }
    }

    private static String namespace(boolean direct) {
        return direct ? EnvelopeNamespace.DIRECT_MESSAGE : EnvelopeNamespace.GROUP;
    }

    private static void cancelTimer(ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private static final class Outgoing {
        final String peerId;
        final FileSender source;
        /** Gan 1 lan truoc khi dua vao map - doc khong can khoa. */
        volatile ReliableChunkSender sender;
        FileTransferStatus status;
        boolean rejected;
        boolean cancelledByPeer;
        long lastEmitAt;

        Outgoing(String peerId, FileSender source, FileTransferStatus status) {
            this.peerId = peerId;
            this.source = source;
            this.status = status;
        }
    }

    private static final class Incoming {
        final String peerId;
        final FileOfferPayload offer;
        FileTransferStatus status;
        FileReceiver receiver;
        /** Cau tra loi da gui - gui lai khi thay offer trung. */
        FileAnswerPayload answer;
        ScheduledFuture<?> delayedAck;
        long lastChunkAt;
        long lastEmitAt;
        int chunksSinceAck;

        Incoming(String peerId, FileOfferPayload offer, FileTransferStatus status) {
            this.peerId = peerId;
            this.offer = offer;
            this.status = status;
        }
    }

    /**
     * File nhan da ket thuc.
     *
     * @param answer       tra loi da gui (gui lai neu offer trung)
     * @param finalAck     ACK {@code verified} neu da nhan xong (gui lai neu chunk den muon)
     * @param cancelReason ly do neu da huy / loi (gui FILE_CANCEL neu chunk den muon)
     */
    private record ClosedIncoming(String peerId, FileAnswerPayload answer, FileAckPayload finalAck,
                                  String cancelReason, long closedAt) {
        ClosedIncoming(String peerId, FileAnswerPayload answer, FileAckPayload finalAck, String cancelReason) {
            this(peerId, answer, finalAck, cancelReason, System.currentTimeMillis());
        }
    }
}
