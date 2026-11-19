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
import com.datn.chatp2p.p2p.filetransfer.ChunkSource;
import com.datn.chatp2p.p2p.filetransfer.FileReceiver;
import com.datn.chatp2p.p2p.filetransfer.FileReceiver.ChunkResult;
import com.datn.chatp2p.p2p.filetransfer.FileSender;
import com.datn.chatp2p.p2p.filetransfer.FileTransferException;
import com.datn.chatp2p.p2p.filetransfer.FileTransferLimits;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dieu phoi giao thuc truyen file 1-1 (docs/Thong-nhat-giao-thuc-truyen-file.md):
 * FILE_OFFER -> FILE_ANSWER -> FILE_CHUNK / FILE_ACK -> ACK cuoi verified, va
 * FILE_CANCEL theo ca 2 chieu. Dung {@link FileSender} / {@link FileReceiver}
 * (p2p-core) cho phan doc / ghi file va {@link OutgoingChunkSender} cho phan
 * gui tin cay.
 *
 * <p>Khong phu thuoc JavaFX hay {@code RoomSession}: gui qua {@link Transport},
 * bao trang thai qua {@code Consumer<FileTransferStatus>} -> test duoc bang 2
 * manager noi voi nhau trong bo nho.</p>
 *
 * <p>Nguyen tac khoa: moi lan truyen co khoa rieng (doi tuong {@code Outgoing} /
 * {@code Incoming}); KHONG BAO GIO goi {@link OutgoingChunkSender} khi dang giu
 * khoa do (bo gui goi nguoc lai listener -> se deadlock).</p>
 */
final class FileTransferManager implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FileTransferManager.class.getName());

    /** Gui 1 payload toi dung 1 peer. */
    interface Transport {
        void send(String peerId, EnvelopeType type, String namespace, Object payload);
    }

    /** Tao bo gui chunk tin cay - doi sang {@code ReliableChunkSender} cua A khi co. */
    interface ChunkSenderFactory {
        OutgoingChunkSender create(ChunkSource source, Consumer<FileChunkPayload> sendChunk,
                                   OutgoingChunkSender.Listener listener, ScheduledExecutorService scheduler);
    }

    static final long ANSWER_TIMEOUT_MS = 60_000;
    static final long RECEIVE_IDLE_TIMEOUT_MS = 30_000;
    /** Gui ACK sau moi bao nhieu chunk moi nhan theo thu tu. */
    static final int ACK_EVERY_CHUNKS = 8;
    /** D4: toi da 64 phan tu trong receivedAbove. */
    static final int ACK_SELECTIVE_LIMIT = 64;
    private static final long PROGRESS_INTERVAL_MS = 100;

    private final Transport transport;
    private final Consumer<FileTransferStatus> listener;
    private final FileTransferLimits limits;
    private final ChunkSenderFactory senderFactory;
    private final long answerTimeoutMs;
    private final long receiveIdleTimeoutMs;
    private final ScheduledExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Incoming> incoming = new ConcurrentHashMap<>();
    /** File da nhan xong -> ACK cuoi, gui lai neu ben gui chua nhan duoc (chunk den muon). */
    private final Map<String, CompletedTransfer> completed = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * Kich thuoc chunk khi gui qua mang that: 512 byte.
     *
     * <p>LY DO (da do that): {@code MergingDatagramSocket} cua ice4j nhan moi goi
     * UDP vao bo dem CO DINH 1500 byte - goi lon hon bi cat cut va
     * {@code P2pDataChannel} am tham bo di. Chunk 4 KB (D5) sau EnvelopeCodec
     * thanh ~7,3 KB nen KHONG BAO GIO toi noi. Chunk 512 byte -> goi ~1,1 KB,
     * vua 1 goi &lt;= 1200 byte (an toan ca voi MTU duong Internet / TURN).
     * Ben nhan van chap nhan moi chunkSize 1..32 KB trong offer.</p>
     */
    static final int NETWORK_CHUNK_SIZE = 512;

    static final FileTransferLimits NETWORK_LIMITS =
            new FileTransferLimits(FileTransferLimits.DEFAULT_MAX_FILE_SIZE, NETWORK_CHUNK_SIZE);

    FileTransferManager(Transport transport, Consumer<FileTransferStatus> listener) {
        this(transport, listener, NETWORK_LIMITS, TemporaryChunkSender::new,
                ANSWER_TIMEOUT_MS, RECEIVE_IDLE_TIMEOUT_MS);
    }

    FileTransferManager(Transport transport, Consumer<FileTransferStatus> listener,
                        FileTransferLimits limits, ChunkSenderFactory senderFactory,
                        long answerTimeoutMs, long receiveIdleTimeoutMs) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.senderFactory = Objects.requireNonNull(senderFactory, "senderFactory");
        this.answerTimeoutMs = answerTimeoutMs;
        this.receiveIdleTimeoutMs = receiveIdleTimeoutMs;
        this.executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "file-transfer");
            thread.setDaemon(true);
            return thread;
        });
        long watchdogPeriod = Math.max(100, Math.min(5_000, receiveIdleTimeoutMs / 3));
        executor.scheduleWithFixedDelay(this::checkIdleIncoming, watchdogPeriod, watchdogPeriod, TimeUnit.MILLISECONDS);
    }

    FileTransferLimits limits() {
        return limits;
    }

    // ================================================================ ben gui

    /**
     * Doc + bam SHA-256 file roi gui FILE_OFFER. Ham nay CHAN (doc ca file) -
     * khong goi tu luong giao dien.
     *
     * @throws FileTransferException    {@code FILE_TOO_LARGE} / {@code IO_ERROR}
     * @throws IllegalArgumentException neu {@code file} khong phai file thuong
     */
    FileTransferStatus sendFile(String peerId, Path file, boolean direct) {
        Objects.requireNonNull(peerId, "peerId");
        ensureOpen();
        FileSender source = FileSender.open(file, limits);
        FileOfferPayload offer = source.offer();
        Outgoing out = new Outgoing(peerId, direct, source, new FileTransferStatus(
                offer.fileId(), peerId, Direction.OUTGOING, direct, offer.fileName(), offer.fileSize(),
                State.WAITING_ANSWER, 0, null, null));
        outgoing.put(offer.fileId(), out);
        emit(out.status);

        if (!trySend(peerId, EnvelopeType.FILE_OFFER, namespace(direct), offer)) {
            finishOutgoing(out, State.FAILED, FileTransferStatus.SEND_FAILED, false);
            return out.status;
        }
        synchronized (out) {
            if (out.status.state() == State.WAITING_ANSWER) {
                out.answerTimeout = executor.schedule(() -> onAnswerTimeout(out),
                        answerTimeoutMs, TimeUnit.MILLISECONDS);
            }
        }
        return out.status;
    }

    private void onAnswerTimeout(Outgoing out) {
        synchronized (out) {
            if (out.status.state() != State.WAITING_ANSWER) {
                return;
            }
        }
        finishOutgoing(out, State.FAILED, "TIMEOUT", true);
    }

    private void onAnswer(String peerId, FileAnswerPayload answer) {
        Outgoing out = outgoing.get(answer.fileId());
        if (out == null || !out.peerId.equals(peerId)) {
            return;
        }
        OutgoingChunkSender sender;
        synchronized (out) {
            if (out.status.state() != State.WAITING_ANSWER) {
                return;
            }
            cancelTimer(out.answerTimeout);
            if (!answer.accepted()) {
                sender = null;
            } else {
                out.status = out.status.with(State.IN_PROGRESS, 0, null, null);
                sender = senderFactory.create(out.source,
                        chunk -> transport.send(out.peerId, EnvelopeType.FILE_CHUNK, null, chunk),
                        new SenderListener(out), executor);
                out.sender = sender;
            }
        }
        if (sender == null) {
            String reason = answer.reason() == null || answer.reason().isBlank() ? "REJECTED" : answer.reason();
            finishOutgoing(out, State.REJECTED, reason, false);
            return;
        }
        emit(out.status);
        sender.start();
    }

    private void onAck(String peerId, FileAckPayload ack) {
        Outgoing out = outgoing.get(ack.fileId());
        if (out == null || !out.peerId.equals(peerId)) {
            return;
        }
        OutgoingChunkSender sender = out.sender;
        if (sender != null) {
            sender.onAck(ack); // NGOAI khoa cua out
        }
    }

    /** Nhan su kien tu bo gui chunk cua 1 file. */
    private final class SenderListener implements OutgoingChunkSender.Listener {
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
                if (now - out.lastEmitAt < PROGRESS_INTERVAL_MS) {
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
            finishOutgoing(out, State.FAILED, reason, true);
        }
    }

    /**
     * Ket thuc 1 lan gui: dung bo gui, dong file, bao giao dien.
     *
     * @param notifyPeer gui FILE_CANCEL(reason) cho ben kia
     */
    private void finishOutgoing(Outgoing out, State state, String reason, boolean notifyPeer) {
        OutgoingChunkSender sender;
        FileTransferStatus status;
        synchronized (out) {
            if (out.status.state().isFinal()) {
                return;
            }
            cancelTimer(out.answerTimeout);
            long bytes = state == State.COMPLETED ? out.status.fileSize() : out.status.bytesTransferred();
            out.status = out.status.with(state, bytes, null, reason);
            status = out.status;
            sender = out.sender;
        }
        outgoing.remove(status.fileId(), out);
        if (sender != null) {
            sender.cancel();
        }
        out.source.close();
        if (notifyPeer) {
            trySend(out.peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(status.fileId(), reason));
        }
        emit(status);
    }

    // ================================================================ ben nhan

    private void onOffer(String peerId, String namespace, FileOfferPayload offer) {
        if (offer.fileId() == null || incoming.containsKey(offer.fileId()) || completed.containsKey(offer.fileId())) {
            return; // offer trung (gui lai) hoac khong co id
        }
        boolean direct = EnvelopeNamespace.DIRECT_MESSAGE.equals(namespace);
        FileTransferStatus offered = new FileTransferStatus(
                offer.fileId(), peerId, Direction.INCOMING, direct, safeDisplayName(offer.fileName()),
                Math.max(0, offer.fileSize()), State.OFFERED, 0, null, null);
        try {
            FileReceiver.validateOffer(offer, limits);
        } catch (FileTransferException e) {
            // Tu choi ngay, khong hoi nguoi dung (file qua lon / offer sai)
            trySend(peerId, EnvelopeType.FILE_ANSWER, null,
                    new FileAnswerPayload(offer.fileId(), false, e.reason().name()));
            emit(offered.with(State.REJECTED, 0, null, e.reason().name()));
            return;
        }
        Incoming in = new Incoming(peerId, offer, offered);
        if (incoming.putIfAbsent(offer.fileId(), in) == null) {
            emit(offered);
        }
    }

    /** Nguoi dung bam "Nhan": tao file tam trong {@code directory} va tra loi FILE_ANSWER. */
    void acceptFile(String fileId, Path directory) {
        Incoming in = incoming.get(fileId);
        if (in == null) {
            return;
        }
        FileTransferStatus status;
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
                // Gui SAU khi da san sang nhan chunk
                trySend(in.peerId, EnvelopeType.FILE_ANSWER, null, new FileAnswerPayload(fileId, true, null));
            } catch (FileTransferException e) {
                // Khong tao duoc file tam (vd thu muc khong ghi duoc) -> bao ben gui
                String reason = e.reason().name();
                trySend(in.peerId, EnvelopeType.FILE_ANSWER, null, new FileAnswerPayload(fileId, false, reason));
                in.status = in.status.with(State.FAILED, 0, null, reason);
                incoming.remove(fileId, in);
            }
            status = in.status;
        }
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
        synchronized (in) {
            if (in.status.state() != State.OFFERED) {
                return;
            }
            in.status = in.status.with(State.REJECTED, 0, null, "REJECTED");
            status = in.status;
        }
        incoming.remove(fileId, in);
        trySend(in.peerId, EnvelopeType.FILE_ANSWER, null, new FileAnswerPayload(fileId, false, "REJECTED"));
        emit(status);
    }

    private void onChunk(String peerId, FileChunkPayload chunk) {
        Incoming in = incoming.get(chunk.fileId());
        if (in == null) {
            CompletedTransfer done = completed.get(chunk.fileId());
            if (done != null && done.peerId.equals(peerId)) {
                // Ben gui chua nhan duoc ACK cuoi -> gui lai
                trySend(peerId, EnvelopeType.FILE_ACK, null, done.finalAck);
            }
            return;
        }
        if (!in.peerId.equals(peerId)) {
            return;
        }
        FileAckPayload ack = null;
        FileTransferStatus progress = null;
        boolean completeNow = false;
        synchronized (in) {
            if (in.receiver == null || in.status.state() != State.IN_PROGRESS) {
                return;
            }
            ChunkResult result;
            try {
                result = in.receiver.onChunk(chunk);
            } catch (FileTransferException e) {
                // Ghi dia that bai - receiver da tu huy va xoa file tam
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, e.reason().name());
                FileTransferStatus failed = in.status;
                incoming.remove(chunk.fileId(), in);
                trySend(peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(chunk.fileId(), e.reason().name()));
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
                in.status = in.status.with(State.VERIFYING, in.offer.fileSize(), null, null);
                progress = in.status;
                completeNow = true;
            } else {
                List<Integer> above = in.receiver.receivedAbove(ACK_SELECTIVE_LIMIT);
                if (result == ChunkResult.DUPLICATE || !above.isEmpty() || in.chunksSinceAck >= ACK_EVERY_CHUNKS) {
                    ack = new FileAckPayload(chunk.fileId(), in.receiver.contiguousChunks(), above, false);
                    in.chunksSinceAck = 0;
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

    /** Kiem SHA-256 + doi ten (chay tren executor, khong chan luong nhan mang). */
    private void completeIncoming(Incoming in) {
        FileTransferStatus status;
        String fileId = in.offer.fileId();
        synchronized (in) {
            if (in.status.state() != State.VERIFYING) {
                return;
            }
            try {
                Path saved = in.receiver.complete();
                FileAckPayload finalAck = new FileAckPayload(fileId, in.offer.totalChunks(), List.of(), true);
                completed.put(fileId, new CompletedTransfer(in.peerId, finalAck));
                in.status = in.status.with(State.COMPLETED, in.offer.fileSize(), saved, null);
            } catch (FileTransferException e) {
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, e.reason().name());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Khong hoan tat duoc file " + fileId, e);
                in.receiver.cancel();
                in.status = in.status.with(State.FAILED, in.status.bytesTransferred(), null, "IO_ERROR");
            }
            status = in.status;
        }
        incoming.remove(fileId, in);
        if (status.state() == State.COMPLETED) {
            FileAckPayload finalAck = completed.get(fileId).finalAck;
            trySend(in.peerId, EnvelopeType.FILE_ACK, null, finalAck);
            if (in.offer.totalChunks() == 0) {
                // File rong: khong co chunk nao de ben gui "nhac" -> gui them 1 lan phong mat goi
                executor.schedule(() -> trySend(in.peerId, EnvelopeType.FILE_ACK, null, finalAck),
                        TemporaryChunkSender.INITIAL_RTO_MS, TimeUnit.MILLISECONDS);
            }
        } else {
            trySend(in.peerId, EnvelopeType.FILE_CANCEL, null, new FileCancelPayload(fileId, status.reason()));
        }
        emit(status);
    }

    private void checkIdleIncoming() {
        long now = System.currentTimeMillis();
        for (Incoming in : incoming.values()) {
            boolean idle;
            synchronized (in) {
                idle = in.status.state() == State.IN_PROGRESS && now - in.lastChunkAt > receiveIdleTimeoutMs;
            }
            if (idle) {
                finishIncoming(in, State.FAILED, "TIMEOUT", true);
            }
        }
    }

    private void finishIncoming(Incoming in, State state, String reason, boolean notifyPeer) {
        FileTransferStatus status;
        synchronized (in) {
            if (in.status.state().isFinal()) {
                return;
            }
            if (in.receiver != null) {
                in.receiver.cancel();
            }
            in.status = in.status.with(state, in.status.bytesTransferred(), null, reason);
            status = in.status;
        }
        incoming.remove(status.fileId(), in);
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
            finishOutgoing(out, State.CANCELLED, "CANCELLED_BY_USER", true);
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
            finishIncoming(in, State.CANCELLED, "CANCELLED_BY_USER", true);
        }
    }

    /** Xu ly 1 Envelope FILE_* tu {@code peerId}. Payload hong thi bo qua. */
    void handleEnvelope(String peerId, Envelope envelope) {
        if (closed || envelope == null || envelope.type() == null) {
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
        boolean byUser = "CANCELLED_BY_USER".equals(cancel.reason());
        State state = byUser ? State.CANCELLED : State.FAILED;
        String reason = byUser ? FileTransferStatus.CANCELLED_BY_PEER : cancel.reason();
        Outgoing out = outgoing.get(cancel.fileId());
        if (out != null && out.peerId.equals(peerId)) {
            finishOutgoing(out, state, reason, false);
            return;
        }
        Incoming in = incoming.get(cancel.fileId());
        if (in != null && in.peerId.equals(peerId)) {
            finishIncoming(in, state, reason, false);
        }
    }

    /** Peer roi phong: dung moi lan truyen voi peer do (khong gui gi them). */
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
        if (closed) {
            return;
        }
        for (Outgoing out : outgoing.values()) {
            finishOutgoing(out, State.CANCELLED, "CANCELLED_BY_USER", true);
        }
        for (Incoming in : incoming.values()) {
            boolean offered;
            synchronized (in) {
                offered = in.status.state() == State.OFFERED;
            }
            if (offered) {
                rejectFile(in.offer.fileId());
            } else {
                finishIncoming(in, State.CANCELLED, "CANCELLED_BY_USER", true);
            }
        }
        closed = true;
        executor.shutdownNow();
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
            LOG.log(Level.FINE, "Khong gui duoc " + type + " toi " + peerId, e);
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

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Da roi phong");
        }
    }

    private static String namespace(boolean direct) {
        return direct ? EnvelopeNamespace.DIRECT_MESSAGE : EnvelopeNamespace.GROUP;
    }

    /** Ten hien thi tren giao dien - dung cung quy tac voi ten se luu. */
    private static String safeDisplayName(String rawName) {
        return com.datn.chatp2p.p2p.filetransfer.FileNames.sanitize(rawName);
    }

    private static void cancelTimer(ScheduledFuture<?> timer) {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private static final class Outgoing {
        final String peerId;
        final boolean direct;
        final FileSender source;
        FileTransferStatus status;
        volatile OutgoingChunkSender sender;
        ScheduledFuture<?> answerTimeout;
        long lastEmitAt;

        Outgoing(String peerId, boolean direct, FileSender source, FileTransferStatus status) {
            this.peerId = peerId;
            this.direct = direct;
            this.source = source;
            this.status = status;
        }
    }

    private static final class Incoming {
        final String peerId;
        final FileOfferPayload offer;
        FileTransferStatus status;
        FileReceiver receiver;
        long lastChunkAt;
        long lastEmitAt;
        int chunksSinceAck;

        Incoming(String peerId, FileOfferPayload offer, FileTransferStatus status) {
            this.peerId = peerId;
            this.offer = offer;
            this.status = status;
        }
    }

    private record CompletedTransfer(String peerId, FileAckPayload finalAck) {
    }
}
