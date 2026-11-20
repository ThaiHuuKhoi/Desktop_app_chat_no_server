package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileAnswerPayload;
import com.datn.chatp2p.common.protocol.FileCancelPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.common.protocol.FileTransferReason;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tang gui file TIN CAY phia gui (A) tren {@code P2pDataChannel} (UDP thuan, khong ACK).
 *
 * <p><b>Vong doi</b>:
 * <ol>
 *   <li>{@link #start()}: gui {@code FILE_OFFER}, gui lai moi {@code offerRetryMillis} cho toi
 *       khi co {@code FILE_ANSWER} (offer/answer cung co the mat goi); qua
 *       {@code answerTimeoutMillis} thi {@code TIMEOUT}.</li>
 *   <li>{@link #onAnswer}: tu choi -> {@code onFailed(reason)}; dong y -> bat dau gui chunk
 *       (file rong 0 chunk -> hoan tat ngay, D3).</li>
 *   <li>Cua so truot: toi da {@code windowSize} chunk dang cho xac nhan. Moi chunk co hen gio
 *       rieng, gui lai voi backoff gap doi (500 ms ... 4 s), toi da {@code maxRetries} lan -
 *       qua thi {@code TIMEOUT}. Moi lan gui lai deu ma hoa lai voi IV moi nen khong bi co che
 *       chong replay cua {@code PeerConnection} chan.</li>
 *   <li>Gui lai nhanh (kieu TCP RACK, {@link #FAST_RETRANSMIT_THRESHOLD}): khong doi het hen gio
 *       neu ACK cho thay da co {@value #FAST_RETRANSMIT_THRESHOLD} chunk GUI SAU chunk do toi noi.
 *       Truoc khi co buoc nay, moi chunk mat lam ca cua so dung ~500 ms (mat 1% goi: 2,4 MB/s con
 *       0,2 MB/s - do bang {@code FileTransferBenchmarkTest}).</li>
 *   <li>{@link #onAck}: {@code contiguousUpTo} + {@code receivedAbove} danh dau chunk da nhan,
 *       khong gui lai chung nua.</li>
 *   <li>Du chunk nhung chua co {@code verified = true}: ben nhan dang kiem SHA-256, HOAC ban ACK
 *       cuoi bi mat. Sender gui lai chunk cuoi theo backoff lam "thu do" - ben nhan da xong
 *       phai tra lai ACK {@code verified} khi thay chunk cua file da hoan tat. Het luot ->
 *       {@code TIMEOUT}.</li>
 *   <li>Chi coi la thanh cong khi nhan {@code verified = true} (D4).</li>
 * </ol>
 *
 * <p><b>Luong</b>: moi trang thai nam trong 1 khoa; {@link #onAck}/{@link #onAnswer}/
 * {@link #onCancel} (thread nhan) va hen gio (scheduler) deu an toan goi dong thoi. Listener
 * duoc goi NGOAI khoa. {@code sendOffer}/{@code sendChunk} duoc goi TRONG khoa - phai nhanh,
 * khong chan (vd {@code roomSession.sendTo}).
 *
 * <p><b>Khong lam</b>: khong tu gui {@code FILE_CANCEL} - khi {@code onFailed} do loi phia
 * minh (TIMEOUT, IO_ERROR), lop goi gui; khi {@link #cancel()}, lop goi gui
 * {@code FILE_CANCEL(CANCELLED_BY_USER)}.
 */
public final class ReliableChunkSender {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(ReliableChunkSender.class.getName());

    private enum State { NEW, OFFERING, SENDING, VERIFYING, COMPLETED, FAILED, CANCELLED }

    private static final class InFlight {
        /** So lan HET HEN GIO lien tiep - quyet dinh backoff va {@code maxRetries} (duong truyen chet). */
        int retries;
        /** So lan gui lai nhanh - khong lam tang backoff, chi bi chan boi {@link #MAX_FAST_RETRANSMITS}. */
        int fastRetransmits;
        ScheduledFuture<?> timer;
        /** So thu tu cua LAN GUI GAN NHAT (dem chung moi lan gui chunk) - dung cho gui lai nhanh. */
        long sentSeq;
    }

    /**
     * Gui lai nhanh (kieu TCP RACK): 1 chunk dang bay duoc coi la MAT khi da co it nhat chung nay
     * chunk GUI SAU no duoc xac nhan - khong doi het hen gio (500 ms+). Lon hon 1 de chiu duoc UDP
     * dao thu tu nhe. Vi so sanh theo LAN GUI (khong theo index), ban gui lai bi mat cung duoc phat
     * hien lai theo dung cach nay.
     */
    static final int FAST_RETRANSMIT_THRESHOLD = 3;

    /**
     * Tran so lan gui lai nhanh cho 1 chunk. Gui lai nhanh dua tren BANG CHUNG (chunk gui sau da toi)
     * nen khong tinh vao {@code maxRetries} - vi {@code maxRetries} de phat hien duong truyen chet
     * (het hen gio lien tiep). Tran nay chi chan vong lap vo han neu ben nhan co loi.
     */
    static final int MAX_FAST_RETRANSMITS = 50;

    private final ChunkSource source;
    private final FileOfferPayload offer;
    private final Consumer<FileOfferPayload> sendOffer;
    private final Consumer<FileChunkPayload> sendChunk;
    private final ReliableTransferListener listener;
    private final ScheduledExecutorService scheduler;
    private final ReliableTransferConfig config;
    private final int totalChunks;

    private final Object lock = new Object();
    private State state = State.NEW;
    private final BitSet acked;
    private int ackedCount;
    private int nextIndex;
    private final Map<Integer, InFlight> inFlight = new HashMap<>();
    /** Dem moi lan gui chunk (ca gui lai) - xem {@link InFlight#sentSeq}. */
    private long nextSendSeq;
    /** {@code sentSeq} lon nhat trong cac chunk da duoc xac nhan. */
    private long highestAckedSeq = -1;
    private ScheduledFuture<?> offerTask;
    private long offerElapsedMillis;
    private ScheduledFuture<?> probeTask;
    private int probeRetries;

    /**
     * @param sendOffer gui {@code FILE_OFFER} toi ben nhan (vd {@code roomSession.sendTo(peerId, FILE_OFFER, o)}).
     * @param sendChunk gui {@code FILE_CHUNK} toi ben nhan. Nem loi = kenh hong -> {@code IO_ERROR}.
     * @param scheduler dung cho hen gio gui lai; co the dung chung, sender khong tu shutdown.
     * @throws IllegalArgumentException neu {@code source.offer()} sai quy uoc D3/D5/D8.
     */
    public ReliableChunkSender(ChunkSource source,
                               Consumer<FileOfferPayload> sendOffer,
                               Consumer<FileChunkPayload> sendChunk,
                               ReliableTransferListener listener,
                               ScheduledExecutorService scheduler,
                               ReliableTransferConfig config) {
        this.source = Objects.requireNonNull(source, "source");
        this.offer = Objects.requireNonNull(source.offer(), "source.offer()");
        this.sendOffer = Objects.requireNonNull(sendOffer, "sendOffer");
        this.sendChunk = Objects.requireNonNull(sendChunk, "sendChunk");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.config = Objects.requireNonNull(config, "config");
        String invalid = FileTransferLimits.validate(offer);
        if (invalid != null) {
            throw new IllegalArgumentException("FILE_OFFER khong hop le: " + invalid);
        }
        if (FileTransferLimits.isTooLarge(offer)) {
            throw new IllegalArgumentException("File vuot gioi han " + FileTransferLimits.MAX_FILE_SIZE + " byte");
        }
        this.totalChunks = offer.totalChunks();
        this.acked = new BitSet(totalChunks);
    }

    public ReliableChunkSender(ChunkSource source,
                               Consumer<FileOfferPayload> sendOffer,
                               Consumer<FileChunkPayload> sendChunk,
                               ReliableTransferListener listener,
                               ScheduledExecutorService scheduler) {
        this(source, sendOffer, sendChunk, listener, scheduler, ReliableTransferConfig.DEFAULT);
    }

    public String fileId() {
        return offer.fileId();
    }

    /** {@code true} khi da hoan tat, that bai hoac bi huy. */
    public boolean isFinished() {
        synchronized (lock) {
            return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
        }
    }

    /** Gui {@code FILE_OFFER} va bat dau cho {@code FILE_ANSWER}. Chi goi 1 lan. */
    public void start() {
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.NEW) {
                throw new IllegalStateException("start() chi duoc goi 1 lan");
            }
            state = State.OFFERING;
            if (transmitOffer(events)) {
                offerTask = schedule(this::onOfferTick, config.offerRetryMillis(), events);
            }
        }
        dispatch(events);
    }

    /** Chuyen moi {@code FILE_ANSWER} nhan duoc tu ben nhan vao day (ban trung bi bo qua). */
    public void onAnswer(FileAnswerPayload answer) {
        if (answer == null || !offer.fileId().equals(answer.fileId())) {
            return;
        }
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.OFFERING) {
                return; // tra loi trung (ben nhan tra lai khi thay offer gui lai)
            }
            cancelTask(offerTask);
            offerTask = null;
            if (!answer.accepted()) {
                fail(answer.reason() == null ? FileTransferReason.REJECTED : answer.reason(), events);
            } else if (totalChunks == 0) {
                complete(events);
            } else {
                state = State.SENDING;
                fillWindow(events);
            }
        }
        dispatch(events);
    }

    /** Chuyen moi {@code FILE_ACK} nhan duoc vao day. */
    public void onAck(FileAckPayload ack) {
        if (ack == null || !offer.fileId().equals(ack.fileId())) {
            return;
        }
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.SENDING && state != State.VERIFYING) {
                return;
            }
            int before = ackedCount;
            int upTo = Math.max(0, Math.min(ack.contiguousUpTo(), totalChunks));
            for (int i = acked.nextClearBit(0); i < upTo; i = acked.nextClearBit(i + 1)) {
                markAcked(i);
            }
            if (ack.receivedAbove() != null) {
                for (Integer index : ack.receivedAbove()) {
                    if (index != null && index >= 0 && index < totalChunks) {
                        markAcked(index);
                    }
                }
            }
            if (ackedCount != before) {
                int acknowledged = ackedCount;
                events.add(() -> listener.onProgress(acknowledged, totalChunks));
            }
            if (ack.verified() && upTo == totalChunks && ackedCount == totalChunks) {
                complete(events);
            } else if (state == State.SENDING) {
                fastRetransmitLost(events);
                fillWindow(events);
            }
        }
        dispatch(events);
    }

    /** Ben kia gui {@code FILE_CANCEL} (vd CHECKSUM_MISMATCH, CANCELLED_BY_USER). */
    public void onCancel(FileCancelPayload cancel) {
        if (cancel == null || !offer.fileId().equals(cancel.fileId())) {
            return;
        }
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            fail(cancel.reason() == null ? FileTransferReason.CANCELLED_BY_USER : cancel.reason(), events);
        }
        dispatch(events);
    }

    /**
     * Huy tu phia minh (nguoi dung bam Huy, peer roi phong...). Khong goi listener; lop goi
     * tu gui {@code FILE_CANCEL} neu can.
     */
    public void cancel() {
        synchronized (lock) {
            if (isTerminal()) {
                return;
            }
            state = State.CANCELLED;
            stopAllTimers();
        }
    }

    // ---------------------------------------------------------------- noi bo (giu khoa)

    private void onOfferTick() {
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.OFFERING) {
                return;
            }
            offerElapsedMillis += config.offerRetryMillis();
            if (offerElapsedMillis >= config.answerTimeoutMillis()) {
                fail(FileTransferReason.TIMEOUT, events);
            } else if (transmitOffer(events)) {
                offerTask = schedule(this::onOfferTick, config.offerRetryMillis(), events);
            }
        }
        dispatch(events);
    }

    private boolean transmitOffer(List<Runnable> events) {
        try {
            sendOffer.accept(offer);
            return true;
        } catch (RuntimeException e) {
            fail(FileTransferReason.IO_ERROR, events);
            return false;
        }
    }

    private void fillWindow(List<Runnable> events) {
        while (state == State.SENDING && inFlight.size() < config.windowSize() && nextIndex < totalChunks) {
            int index = nextIndex++;
            if (acked.get(index)) {
                continue;
            }
            InFlight entry = new InFlight();
            inFlight.put(index, entry);
            transmitChunk(index, entry, events);
        }
        if (state == State.SENDING && ackedCount == totalChunks) {
            state = State.VERIFYING;
            probeRetries = 0;
            probeTask = schedule(this::onProbeTick, config.backoffMillis(0), events);
        }
    }

    private void transmitChunk(int index, InFlight entry, List<Runnable> events) {
        entry.sentSeq = nextSendSeq++;
        if (!sendChunkNow(index, events)) {
            return;
        }
        int retries = entry.retries;
        entry.timer = schedule(() -> onChunkTimeout(index), config.backoffMillis(retries), events);
    }

    /**
     * Gui lai ngay cac chunk ma ben nhan da nhan duoc nhieu chunk gui SAU chung - xem
     * {@link #FAST_RETRANSMIT_THRESHOLD}. Bang chung chunk sau da toi cho thay duong truyen con song,
     * nen dat lai bo dem het hen gio (backoff quay ve muc dau).
     */
    private void fastRetransmitLost(List<Runnable> events) {
        if (highestAckedSeq < FAST_RETRANSMIT_THRESHOLD) {
            return;
        }
        List<Integer> lost = new ArrayList<>();
        for (Map.Entry<Integer, InFlight> e : inFlight.entrySet()) {
            if (e.getValue().sentSeq + FAST_RETRANSMIT_THRESHOLD <= highestAckedSeq) {
                lost.add(e.getKey());
            }
        }
        lost.sort(null); // lap lo theo thu tu file: ben nhan tien contiguousUpTo som nhat
        for (int index : lost) {
            InFlight entry = inFlight.get(index);
            if (state != State.SENDING || entry == null) {
                return;
            }
            if (entry.fastRetransmits >= MAX_FAST_RETRANSMITS) {
                fail(FileTransferReason.TIMEOUT, events);
                return;
            }
            cancelTask(entry.timer);
            entry.fastRetransmits++;
            entry.retries = 0;
            transmitChunk(index, entry, events);
        }
    }

    private boolean sendChunkNow(int index, List<Runnable> events) {
        FileChunkPayload chunk;
        try {
            chunk = source.readChunk(index);
        } catch (IOException | RuntimeException e) {
            fail(FileTransferReason.IO_ERROR, events);
            return false;
        }
        try {
            sendChunk.accept(chunk);
            return true;
        } catch (RuntimeException e) {
            fail(FileTransferReason.IO_ERROR, events);
            return false;
        }
    }

    private void onChunkTimeout(int index) {
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.SENDING) {
                return;
            }
            InFlight entry = inFlight.get(index);
            if (entry == null) {
                return; // da duoc xac nhan trong luc hen gio dang chay
            }
            if (entry.retries >= config.maxRetries()) {
                fail(FileTransferReason.TIMEOUT, events);
            } else {
                entry.retries++;
                transmitChunk(index, entry, events);
            }
        }
        dispatch(events);
    }

    private void onProbeTick() {
        List<Runnable> events = new ArrayList<>();
        synchronized (lock) {
            if (state != State.VERIFYING) {
                return;
            }
            if (probeRetries >= config.maxRetries()) {
                fail(FileTransferReason.TIMEOUT, events);
            } else {
                probeRetries++;
                // Gui lai chunk cuoi: ben nhan da xong se tra lai ACK verified; ben nhan con dang
                // kiem SHA-256 thi chi coi la ban trung.
                if (sendChunkNow(totalChunks - 1, events)) {
                    probeTask = schedule(this::onProbeTick, config.backoffMillis(probeRetries), events);
                }
            }
        }
        dispatch(events);
    }

    private void markAcked(int index) {
        if (acked.get(index)) {
            return;
        }
        acked.set(index);
        ackedCount++;
        InFlight entry = inFlight.remove(index);
        if (entry != null) {
            cancelTask(entry.timer);
            highestAckedSeq = Math.max(highestAckedSeq, entry.sentSeq);
        }
    }

    private void complete(List<Runnable> events) {
        if (isTerminal()) {
            return;
        }
        state = State.COMPLETED;
        stopAllTimers();
        events.add(listener::onCompleted);
    }

    private void fail(String reason, List<Runnable> events) {
        if (isTerminal()) {
            return;
        }
        if (FileTransferReason.TIMEOUT.equals(reason)) {
            State at = state;
            String waiting = inFlight.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(12)
                    .map(e -> e.getKey() + ":" + e.getValue().retries + "/" + e.getValue().fastRetransmits)
                    .toList().toString();
            LOG.warning(() -> "Gui file " + offer.fileId() + " TIMEOUT o trang thai " + at + ": da xac nhan "
                    + ackedCount + "/" + totalChunks + ", dang cho (chunk:het gio/gui lai nhanh) " + waiting);
        }
        state = State.FAILED;
        stopAllTimers();
        events.add(() -> listener.onFailed(reason));
    }

    private boolean isTerminal() {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }

    private void stopAllTimers() {
        cancelTask(offerTask);
        offerTask = null;
        cancelTask(probeTask);
        probeTask = null;
        for (InFlight entry : inFlight.values()) {
            cancelTask(entry.timer);
        }
        inFlight.clear();
    }

    private ScheduledFuture<?> schedule(Runnable task, long delayMillis, List<Runnable> events) {
        try {
            return scheduler.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            fail(FileTransferReason.IO_ERROR, events); // scheduler da shutdown (vd roi phong)
            return null;
        }
    }

    private static void cancelTask(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void dispatch(List<Runnable> events) {
        for (Runnable event : events) {
            event.run();
        }
    }
}
