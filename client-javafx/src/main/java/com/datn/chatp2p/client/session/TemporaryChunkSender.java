package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.p2p.filetransfer.ChunkSource;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * BO GUI TAM THOI - de demo truyen file truoc khi A hoan thanh
 * {@code ReliableChunkSender}. Theo dung cac tham so da thong nhat (D5, D8):
 * cua so truot 64 chunk (chunk 512 byte), ACK chon loc, RTO 500 ms nhan doi toi 4 s, gui lai
 * toi da 10 lan / chunk.
 *
 * <p>Nguyen tac khoa: moi trang thai sua trong {@code synchronized}, con viec
 * doc chunk, gui mang va goi {@link OutgoingChunkSender.Listener} luon lam
 * NGOAI khoa - tranh deadlock voi {@link FileTransferManager}.</p>
 */
final class TemporaryChunkSender implements OutgoingChunkSender {

    /**
     * 64 chunk x 512 byte = 32 KB dang bay. Khong tang qua ~90: hang doi nhan
     * cua ice4j ({@code MergingDatagramSocket}) chi giu 100 goi, du se bi bo.
     */
    static final int WINDOW_SIZE = 64;
    static final long INITIAL_RTO_MS = 500;
    static final long MAX_RTO_MS = 4_000;
    static final int MAX_RETRIES = 10;
    private static final long TICK_MS = 100;

    private final ChunkSource source;
    private final Consumer<FileChunkPayload> sendChunk;
    private final Listener listener;
    private final ScheduledExecutorService scheduler;
    private final int totalChunks;

    private final BitSet acked = new BitSet();
    /** Chunk da gui nhung chua duoc ACK -> thoi diem gui gan nhat. */
    private final Map<Integer, Long> inFlight = new HashMap<>();
    private final Map<Integer, Integer> retries = new HashMap<>();
    private int ackedCount;
    private int nextIndex;
    private long rtoMs = INITIAL_RTO_MS;
    /** Da ACK du chunk, dang cho ACK cuoi verified - thoi diem bat dau cho / lan nhac gan nhat. */
    private long lastNudgeAt;
    private int nudges;
    private boolean finished;
    private ScheduledFuture<?> tickTask;

    TemporaryChunkSender(ChunkSource source, Consumer<FileChunkPayload> sendChunk,
                         Listener listener, ScheduledExecutorService scheduler) {
        this.source = source;
        this.sendChunk = sendChunk;
        this.listener = listener;
        this.scheduler = scheduler;
        this.totalChunks = source.offer().totalChunks();
    }

    @Override
    public void start() {
        List<Integer> toSend;
        synchronized (this) {
            if (finished) {
                return;
            }
            lastNudgeAt = System.currentTimeMillis();
            toSend = fillWindow();
            tickTask = scheduler.scheduleWithFixedDelay(this::onTick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
        }
        send(toSend);
    }

    @Override
    public void onAck(FileAckPayload ack) {
        List<Integer> toSend;
        boolean progressed;
        boolean completed = false;
        int ackedNow;
        synchronized (this) {
            if (finished || ack == null) {
                return;
            }
            int before = ackedCount;
            int contiguous = Math.min(Math.max(ack.contiguousUpTo(), 0), totalChunks);
            for (int i = acked.nextClearBit(0); i < contiguous; i = acked.nextClearBit(i + 1)) {
                markAcked(i);
            }
            if (ack.receivedAbove() != null) {
                for (Integer index : ack.receivedAbove()) {
                    if (index != null && index >= 0 && index < totalChunks && !acked.get(index)) {
                        markAcked(index);
                    }
                }
            }
            progressed = ackedCount > before;
            if (progressed) {
                rtoMs = INITIAL_RTO_MS;
                lastNudgeAt = System.currentTimeMillis();
                nudges = 0;
            }
            ackedNow = ackedCount;
            if (ack.verified()) {
                completed = true;
                stop();
                toSend = List.of();
            } else {
                toSend = fillWindow();
            }
        }
        send(toSend);
        if (progressed || completed) {
            listener.onProgress(completed ? totalChunks : ackedNow, totalChunks);
        }
        if (completed) {
            listener.onCompleted();
        }
    }

    @Override
    public synchronized void cancel() {
        stop();
    }

    private void onTick() {
        List<Integer> toSend = new ArrayList<>();
        boolean timedOut = false;
        synchronized (this) {
            if (finished) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean resent = false;
            for (Map.Entry<Integer, Long> entry : inFlight.entrySet()) {
                if (now - entry.getValue() < rtoMs) {
                    continue;
                }
                int index = entry.getKey();
                int count = retries.merge(index, 1, Integer::sum);
                if (count > MAX_RETRIES) {
                    timedOut = true;
                    break;
                }
                entry.setValue(now);
                toSend.add(index);
                resent = true;
            }
            if (!timedOut && ackedCount == totalChunks && now - lastNudgeAt >= rtoMs) {
                // Da ACK du chunk nhung chua co ACK cuoi verified (co the bi mat):
                // gui lai chunk cuoi de ben nhan gui lai ACK cuoi.
                if (++nudges > MAX_RETRIES) {
                    timedOut = true;
                } else {
                    lastNudgeAt = now;
                    resent = true;
                    if (totalChunks > 0) {
                        toSend.add(totalChunks - 1);
                    }
                }
            }
            if (resent) {
                rtoMs = Math.min(rtoMs * 2, MAX_RTO_MS);
            }
            if (timedOut) {
                stop();
                toSend.clear();
            }
        }
        if (timedOut) {
            listener.onFailed("TIMEOUT");
            return;
        }
        send(toSend);
    }

    /** Lay them chunk moi cho toi khi so chunk dang bay = kich thuoc cua so. Goi trong khoa. */
    private List<Integer> fillWindow() {
        List<Integer> toSend = new ArrayList<>();
        long now = System.currentTimeMillis();
        while (inFlight.size() < WINDOW_SIZE && nextIndex < totalChunks) {
            if (!acked.get(nextIndex)) {
                inFlight.put(nextIndex, now);
                toSend.add(nextIndex);
            }
            nextIndex++;
        }
        return toSend;
    }

    private void markAcked(int index) {
        acked.set(index);
        ackedCount++;
        inFlight.remove(index);
        retries.remove(index);
    }

    private void stop() {
        finished = true;
        if (tickTask != null) {
            tickTask.cancel(false);
        }
    }

    /** Doc va gui chunk - NGOAI khoa. */
    private void send(List<Integer> indexes) {
        for (int index : indexes) {
            synchronized (this) {
                if (finished) {
                    return;
                }
            }
            try {
                sendChunk.accept(source.readChunk(index));
            } catch (RuntimeException e) {
                boolean firstFailure;
                synchronized (this) {
                    firstFailure = !finished;
                    stop();
                }
                if (firstFailure) {
                    listener.onFailed("IO_ERROR");
                }
                return;
            }
        }
    }
}
