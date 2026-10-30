package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.protocol.EnvelopeCodec;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra toan bo vong doi cua {@link PeerConnection} tren 1
 * {@link LoopbackDataChannel} (khong can ICE/mang that): trao khoa ECDH tu
 * dong ngay khi tao xong, roi gui/nhan {@link Envelope} da ma hoa dung -
 * dung nhu se xay ra that su sau khi {@code IceP2pConnectionEstablisher}
 * bao COMPLETED (xem Tai-lieu-ky-thuat.md Phan E.4).
 */
class PeerConnectionTest {

    @Test
    void handshakeCompletesAndEncryptedMessageIsExchanged() throws Exception {
        LoopbackDataChannel.Pair channels = LoopbackDataChannel.createPair();
        KeyPair keyPairA = KeyExchangeService.generateKeyPair();
        KeyPair keyPairB = KeyExchangeService.generateKeyPair();

        CountDownLatch bothHandshakeDone = new CountDownLatch(2);
        BlockingQueue<Envelope> bInbox = new ArrayBlockingQueue<>(10);

        PeerConnection peerA = new PeerConnection(
                "peer-b", channels.endpointA(), keyPairA,
                (from, envelope) -> { /* A khong mong nhan gi trong test nay */ },
                bothHandshakeDone::countDown);

        PeerConnection peerB = new PeerConnection(
                "peer-a", channels.endpointB(), keyPairB,
                (from, envelope) -> bInbox.add(envelope),
                bothHandshakeDone::countDown);

        assertTrue(peerA.getVerificationState() != null, "verificationState mac dinh khong duoc null");
        assertThrows(IllegalStateException.class,
                () -> peerA.send(EnvelopeType.MESSAGE, new MessagePayload("x", "peer-b", "qua som", 0L)),
                "send() truoc khi handshake xong phai bao loi ro rang, khong duoc gui ngam");

        // Ca 2 phia tu gui public key ECDH cua minh - khong ai "hoi truoc", doi xung hoan toan.
        peerA.sendEcdhPublicKey();
        peerB.sendEcdhPublicKey();

        assertTrue(bothHandshakeDone.await(5, TimeUnit.SECONDS), "Ca 2 ben phai hoan tat trao khoa ECDH trong 5s");
        assertTrue(peerA.isHandshakeComplete());
        assertTrue(peerB.isHandshakeComplete());

        MessagePayload sent = new MessagePayload("msg-1", "peer-a", "xin chao qua Envelope", 12_345L);
        peerA.send(EnvelopeType.MESSAGE, EnvelopeNamespace.GROUP, sent);

        Envelope received = bInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "B phai nhan duoc Envelope trong 5s");
        assertEquals(EnvelopeType.MESSAGE, received.type());
        assertEquals(EnvelopeNamespace.GROUP, received.namespace());

        // B tu parse payload bang chinh EnvelopeCodec cua minh (khong dung lai cua A) -
        // chi de xac nhan dinh dang JSON dung, khong lien quan gi den khoa AES (khoa
        // giai ma da nam san trong Envelope truoc do, o buoc PeerConnection.decode noi bo).
        EnvelopeCodec codecJustForParsing = new EnvelopeCodec(
                KeyExchangeService.deriveSharedSecret(keyPairB.getPrivate(), keyPairA.getPublic()));
        MessagePayload parsed = codecJustForParsing.parsePayload(received, MessagePayload.class);
        assertEquals(sent, parsed);

        peerA.close();
        peerB.close();
    }
}
