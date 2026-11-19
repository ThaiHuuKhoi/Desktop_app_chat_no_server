package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.FileAckPayload;
import com.datn.chatp2p.common.protocol.FileAnswerPayload;
import com.datn.chatp2p.common.protocol.FileCancelPayload;
import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.LoopbackDataChannel;
import com.datn.chatp2p.p2p.NetworkImpairment;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.UnreliableDataChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghep tron tang duoi THAT: {@link PeerConnection} (ECDH + AES-GCM + chong replay, Envelope
 * JSON) tren kenh gia lap UDP xau ca 2 chieu. Xac nhan:
 * <ul>
 *   <li>chunk 4 KB qua Base64 x2 + ma hoa van gui duoc;</li>
 *   <li>goi bi nhan doi (cung ciphertext) bi PeerConnection loai, con ban GUI LAI (ma hoa lai,
 *       IV moi) van di qua;</li>
 *   <li>du lieu nhan dung tung byte.</li>
 * </ul>
 */
class FileTransferOverUnreliablePeerConnectionTest {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void shutdown() {
        scheduler.shutdownNow();
    }

    @Test
    void oneMegabyteFileSurvivesLossDuplicationAndReordering() throws Exception {
        NetworkImpairment senderToReceiver = new NetworkImpairment(scheduler, 21, 0.15, 0.05, 20);
        NetworkImpairment receiverToSender = new NetworkImpairment(scheduler, 22, 0.15, 0.05, 20);
        senderToReceiver.setEnabled(false); // handshake ECDH sach truoc, roi moi bat suy giam
        receiverToSender.setEnabled(false);

        LoopbackDataChannel.Pair pair = LoopbackDataChannel.createPair();
        AtomicReference<ReliableChunkSender> senderRef = new AtomicReference<>();
        AtomicReference<TestFileReceiver> receiverRef = new AtomicReference<>();
        CountDownLatch handshakes = new CountDownLatch(2);

        PeerConnection senderSide = new PeerConnection("receiver",
                new UnreliableDataChannel(pair.endpointA(), senderToReceiver),
                KeyExchangeService.generateKeyPair(),
                (from, envelope) -> {
                    ReliableChunkSender sender = senderRef.get();
                    switch (envelope.type()) {
                        case FILE_ANSWER -> sender.onAnswer(parse(envelope, FileAnswerPayload.class));
                        case FILE_ACK -> sender.onAck(parse(envelope, FileAckPayload.class));
                        case FILE_CANCEL -> sender.onCancel(parse(envelope, FileCancelPayload.class));
                        default -> { }
                    }
                },
                handshakes::countDown);
        PeerConnection receiverSide = new PeerConnection("sender",
                new UnreliableDataChannel(pair.endpointB(), receiverToSender),
                KeyExchangeService.generateKeyPair(),
                (from, envelope) -> {
                    TestFileReceiver receiver = receiverRef.get();
                    switch (envelope.type()) {
                        case FILE_OFFER -> receiver.onOffer(parse(envelope, FileOfferPayload.class));
                        case FILE_CHUNK -> receiver.onChunk(parse(envelope, FileChunkPayload.class));
                        default -> { }
                    }
                },
                handshakes::countDown);

        senderSide.sendEcdhPublicKey();
        receiverSide.sendEcdhPublicKey();
        assertTrue(handshakes.await(5, TimeUnit.SECONDS), "Handshake ECDH phai xong");
        senderToReceiver.setEnabled(true);
        receiverToSender.setEnabled(true);

        receiverRef.set(new TestFileReceiver(
                answer -> receiverSide.send(EnvelopeType.FILE_ANSWER, answer),
                ack -> receiverSide.send(EnvelopeType.FILE_ACK, ack),
                cancel -> receiverSide.send(EnvelopeType.FILE_CANCEL, cancel),
                scheduler, true));

        InMemoryChunkSource source = InMemoryChunkSource.random(1024 * 1024, FileTransferLimits.DEFAULT_CHUNK_SIZE, 9);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        senderRef.set(new ReliableChunkSender(source,
                offer -> senderSide.send(EnvelopeType.FILE_OFFER, offer),
                chunk -> senderSide.send(EnvelopeType.FILE_CHUNK, chunk),
                new ReliableTransferListener() {
                    @Override
                    public void onProgress(int ackedChunks, int totalChunks) {
                    }

                    @Override
                    public void onCompleted() {
                        finished.countDown();
                    }

                    @Override
                    public void onFailed(String reason) {
                        failure.set(reason);
                        finished.countDown();
                    }
                },
                scheduler,
                new ReliableTransferConfig(32, 150, 1_000, 10, 200, 5_000)));

        long start = System.nanoTime();
        senderRef.get().start();

        assertTrue(finished.await(60, TimeUnit.SECONDS), "Truyen 1 MB phai xong trong 60s");
        assertNull(failure.get(), "Truyen file that bai: " + failure.get());
        assertArrayEquals(source.data(), receiverRef.get().result());
        System.out.printf("1 MB qua PeerConnection + mat 15%%/nhan doi 5%%: %d ms, %d goi bi mat, %d goi nhan doi%n",
                (System.nanoTime() - start) / 1_000_000,
                senderToReceiver.droppedCount() + receiverToSender.droppedCount(),
                senderToReceiver.duplicatedCount() + receiverToSender.duplicatedCount());

        senderSide.close();
        receiverSide.close();
    }

    private <T> T parse(Envelope envelope, Class<T> type) {
        try {
            return mapper.readValue(envelope.payload(), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
