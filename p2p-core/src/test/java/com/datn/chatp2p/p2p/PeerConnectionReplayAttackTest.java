package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.channel.P2pDataChannel;
import com.datn.chatp2p.p2p.protocol.EnvelopeCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra bao mat Tang 4: {@link PeerConnection} co chong duoc TAN CONG
 * REPLAY (ke tan cong bat duoc 1 goi tin da MA HOA hop le tren duong truyen,
 * roi PHAT LAI y nguyen sau do) hay khong - xem javadoc muc "Chong tan cong
 * REPLAY" cua {@link PeerConnection}. AES-GCM chi dam bao TINH TOAN VEN/XAC
 * THUC noi dung (khong ai sua duoc ma khong bi phat hien) - KHONG tu dong
 * dam bao "tuoi moi" (freshness): 1 ban sao CHINH XAC cua ciphertext hop le
 * van giai ma THANH CONG (dung tag, dung khoa) neu khong co gi ngan.
 *
 * <p>Mo phong dung kich ban tan cong: "ke tan cong" bat duoc CHINH XAC cac
 * byte tho ma A da gui cho B (o day gia lap bang cach tu tao ciphertext qua
 * EnvelopeCodec, dung cach lam nhu {@code PeerConnectionMaxPayloadSizeTest}),
 * roi gui LAI y nguyen ban sao do 1 lan nua qua kenh - kiem tra xem B co
 * "tin" va xu ly lai lan 2 hay khong (khong duoc).
 */
class PeerConnectionReplayAttackTest {

    private DatagramSocket socketA;
    private DatagramSocket socketB;
    private PeerConnection peerA;
    private PeerConnection peerB;

    @AfterEach
    void tearDown() {
        if (peerA != null) peerA.close();
        if (peerB != null) peerB.close();
    }

    @Test
    void resendingAnAlreadyDeliveredCiphertextIsRejectedAsAReplayInsteadOfBeingProcessedAgain() throws Exception {
        socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        InetSocketAddress addressA = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort());
        InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

        P2pDataChannel channelA = new P2pDataChannel(socketA, addressB);
        P2pDataChannel channelB = new P2pDataChannel(socketB, addressA);

        KeyPair keyPairA = KeyExchangeService.generateKeyPair();
        KeyPair keyPairB = KeyExchangeService.generateKeyPair();

        CountDownLatch bothHandshakeDone = new CountDownLatch(2);
        List<Envelope> receivedByB = new ArrayList<>();
        peerA = new PeerConnection("peer-b", channelA, keyPairA,
                (from, envelope) -> { }, bothHandshakeDone::countDown);
        peerB = new PeerConnection("peer-a", channelB, keyPairB,
                (from, envelope) -> receivedByB.add(envelope), bothHandshakeDone::countDown);

        peerA.sendEcdhPublicKey();
        peerB.sendEcdhPublicKey();
        assertTrue(bothHandshakeDone.await(5, TimeUnit.SECONDS), "Ca 2 ben phai hoan tat trao khoa ECDH trong 5s");

        // Tu tao CHINH XAC cung 1 ciphertext ma peerA.send() se tao ra (dung
        // chung khoa phien) - mo phong "ke tan cong da bat duoc" dung goi tin
        // nay tren duong truyen, roi gui thang qua channelA (bo qua PeerConnection.send()
        // de kiem soat chinh xac viec gui 2 LAN Y HET nhau).
        EnvelopeCodec codecMirroringPeerA = new EnvelopeCodec(
                KeyExchangeService.deriveSharedSecret(keyPairA.getPrivate(), keyPairB.getPublic()));
        byte[] capturedCiphertext = codecMirroringPeerA.encode(
                EnvelopeType.MESSAGE, new MessagePayload("m-1", "peer-a", "ban tin can bi bat lai", 1L));

        channelA.send(capturedCiphertext);
        waitUntil(() -> receivedByB.size() >= 1, 5);
        assertEquals(1, receivedByB.size(), "B phai nhan dung 1 lan cho lan gui THAT dau tien");

        // "Ke tan cong" gui LAI y nguyen CHINH XAC ban sao ciphertext da bat
        // duoc o tren - khong sua doi gi ca. Doi 1 khoang hop ly de chac chan
        // KHONG co xu ly lai muon (khong the "waitUntil size>=2" nua vi dung
        // hanh vi mong doi bay gio la KHONG BAO GIO len 2).
        channelA.send(capturedCiphertext);
        Thread.sleep(500);

        System.out.println("So lan B xu ly cho DUNG 1 ban tin da bi phat lai: " + receivedByB.size()
                + " (phai la 1 - lan phat lai phai bi loc bo boi cua so chong replay)");
        assertEquals(1, receivedByB.size(),
                "Ban tin bi phat lai (gui lai y nguyen ciphertext da gui truoc do) phai bi loc bo - "
                        + "chi tinh la 1 lan xu ly THAT, khong duoc xu ly lai lan 2");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, int timeoutSeconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }
}
