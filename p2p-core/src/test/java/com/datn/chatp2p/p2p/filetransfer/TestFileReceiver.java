package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileAnswerPayload;
import com.datn.chatp2p.common.protocol.FileCancelPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.common.protocol.FileTransferReason;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ben nhan TOI THIEU cho test cua A, cai dat dung quy tac phia nhan da thong nhat (D4 + bo sung):
 * <ul>
 *   <li>offer trung -&gt; tra lai dung cau tra loi cu (khong hoi lai);</li>
 *   <li>FILE_ACK sau moi 8 chunk moi, hoac 50 ms khong co chunk moi;</li>
 *   <li>du chunk -&gt; kiem SHA-256 -&gt; ACK {@code verified} hoac FILE_CANCEL(CHECKSUM_MISMATCH);</li>
 *   <li>da xong ma con nhan chunk cua file do -&gt; gui LAI ACK {@code verified} (ACK cuoi co the mat).</li>
 * </ul>
 * B lam ban that ({@code FileReceiver}) ghi ra dia; ban nay giu trong bo nho.
 */
final class TestFileReceiver {

    static final int ACK_EVERY_CHUNKS = 8;
    static final long ACK_IDLE_MILLIS = 50;
    static final int MAX_RECEIVED_ABOVE = 64;

    private final Consumer<FileAnswerPayload> sendAnswer;
    private final Consumer<FileAckPayload> sendAck;
    private final Consumer<FileCancelPayload> sendCancel;
    private final ScheduledExecutorService scheduler;
    private final boolean accept;

    private FileOfferPayload offer;
    private byte[][] chunks;
    private int receivedCount;
    private int sinceLastAck;
    private ScheduledFuture<?> idleAck;
    private boolean finished;
    private boolean verified;
    private byte[] result;
    private int rejectedChunks;
    final CountDownLatch done = new CountDownLatch(1);

    TestFileReceiver(Consumer<FileAnswerPayload> sendAnswer, Consumer<FileAckPayload> sendAck,
                     Consumer<FileCancelPayload> sendCancel, ScheduledExecutorService scheduler, boolean accept) {
        this.sendAnswer = sendAnswer;
        this.sendAck = sendAck;
        this.sendCancel = sendCancel;
        this.scheduler = scheduler;
        this.accept = accept;
    }

    synchronized void onOffer(FileOfferPayload incoming) {
        if (offer != null && offer.fileId().equals(incoming.fileId())) {
            answer(); // offer gui lai -> tra loi lai, khong hoi nguoi dung lan nua
            return;
        }
        offer = incoming;
        chunks = new byte[incoming.totalChunks()][];
        answer();
        if (accept && incoming.totalChunks() == 0) {
            finish();
        }
    }

    private void answer() {
        sendAnswer.accept(accept
                ? new FileAnswerPayload(offer.fileId(), true, null)
                : new FileAnswerPayload(offer.fileId(), false, FileTransferReason.REJECTED));
    }

    synchronized void onChunk(FileChunkPayload chunk) {
        if (offer == null || !offer.fileId().equals(chunk.fileId()) || !accept) {
            return;
        }
        if (finished) {
            if (verified) {
                sendAck.accept(finalAck()); // ACK cuoi truoc do co the da mat
            }
            return;
        }
        int index = chunk.index();
        if (index < 0 || index >= offer.totalChunks()
                || chunk.data() == null
                || chunk.data().length != FileTransferLimits.expectedChunkLength(offer, index)) {
            rejectedChunks++;
            return;
        }
        if (chunks[index] != null) {
            return; // ban trung
        }
        chunks[index] = chunk.data();
        receivedCount++;
        sinceLastAck++;
        if (receivedCount == offer.totalChunks()) {
            finish();
        } else if (sinceLastAck >= ACK_EVERY_CHUNKS) {
            flushAck();
        } else {
            scheduleIdleAck();
        }
    }

    private void scheduleIdleAck() {
        if (idleAck != null) {
            idleAck.cancel(false);
        }
        try {
            idleAck = scheduler.schedule(() -> {
                synchronized (TestFileReceiver.this) {
                    if (!finished && sinceLastAck > 0) {
                        flushAck();
                    }
                }
            }, ACK_IDLE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // test da ket thuc
        }
    }

    private void flushAck() {
        sinceLastAck = 0;
        int contiguous = contiguous();
        List<Integer> above = new ArrayList<>();
        for (int i = contiguous + 1; i < chunks.length && above.size() < MAX_RECEIVED_ABOVE; i++) {
            if (chunks[i] != null) {
                above.add(i);
            }
        }
        sendAck.accept(new FileAckPayload(offer.fileId(), contiguous, above, false));
    }

    private int contiguous() {
        int i = 0;
        while (i < chunks.length && chunks[i] != null) {
            i++;
        }
        return i;
    }

    private void finish() {
        finished = true;
        if (idleAck != null) {
            idleAck.cancel(false);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] c : chunks) {
            out.writeBytes(c);
        }
        byte[] bytes = out.toByteArray();
        if (InMemoryChunkSource.sha256Hex(bytes).equals(offer.sha256())) {
            verified = true;
            result = bytes;
            sendAck.accept(finalAck());
        } else {
            sendCancel.accept(new FileCancelPayload(offer.fileId(), FileTransferReason.CHECKSUM_MISMATCH));
        }
        done.countDown();
    }

    private FileAckPayload finalAck() {
        return new FileAckPayload(offer.fileId(), offer.totalChunks(), List.of(), true);
    }

    synchronized byte[] result() {
        return result;
    }

    synchronized int rejectedChunks() {
        return rejectedChunks;
    }
}
