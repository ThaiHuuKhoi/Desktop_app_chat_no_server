package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.channel.P2pDataChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra kha nang mo rong cua Tang 4 o goc do THONG LUONG: gui MOT LOAT
 * Envelope LIEN TIEP (khong co khoang nghi) qua {@code EnvelopeCodec}/
 * {@code PeerConnection} that tren UDP that, xac nhan thuc te bao nhieu ban
 * tin toi noi - KHAC voi goi tin ECDH public key (da co retry, xem
 * {@code PeerConnection#sendEcdhPublicKey}), Envelope thuong (MESSAGE...) hoan
 * toan KHONG co ACK/retry o tang nao (Tai-lieu-ky-thuat.md Phan H.3) - dung
 * nguyen si kha nang "gui roi thoi" cua UDP.
 *
 * <p>Tren localhost (khong co tac nghen mang that), du kien mat mat gan nhu
 * bang 0 - nhung day la GIA DINH chua tung duoc do dac that, khong phai da
 * biet truoc. Neu co mat mat that tren localhost, do se la dau hieu ro rang
 * ve 1 gioi han that (vd buffer nhan cua OS/socket qua nho so voi toc do gui).
 */
class PeerConnectionBurstThroughputTest {

    private static final int MESSAGE_COUNT = 1_000;

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
    void measuresHowManyRapidlySentMessagesActuallyArriveOverRealUdp() throws Exception {
        socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        InetSocketAddress addressA = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort());
        InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

        P2pDataChannel channelA = new P2pDataChannel(socketA, addressB);
        P2pDataChannel channelB = new P2pDataChannel(socketB, addressA);

        KeyPair keyPairA = KeyExchangeService.generateKeyPair();
        KeyPair keyPairB = KeyExchangeService.generateKeyPair();

        CountDownLatch bothHandshakeDone = new CountDownLatch(2);
        // Dung Set thay vi List/counter don gian - CopyOnWriteArraySet du an toan
        // voi ghi dong thoi tu 1 thread nhan duy nhat (P2pDataChannel chi co 1
        // thread nhan/kenh), nhung dung Set con giup phat hien neu co ban tin nao
        // bi TRUNG (khong chi thieu) - vd 2 id giong het nhau toi 2 lan.
        Set<String> receivedIds = ConcurrentHashMap.newKeySet();
        CountDownLatch allReceived = new CountDownLatch(MESSAGE_COUNT);

        peerA = new PeerConnection("peer-b", channelA, keyPairA,
                (from, envelope) -> { }, bothHandshakeDone::countDown);
        peerB = new PeerConnection("peer-a", channelB, keyPairB,
                (from, envelope) -> {
                    MessagePayload payload = new com.datn.chatp2p.p2p.protocol.EnvelopeCodec(
                                    KeyExchangeService.deriveSharedSecret(keyPairB.getPrivate(), keyPairA.getPublic()))
                            .parsePayload(envelope, MessagePayload.class);
                    if (receivedIds.add(payload.id())) {
                        allReceived.countDown();
                    }
                },
                bothHandshakeDone::countDown);

        peerA.sendEcdhPublicKey();
        peerB.sendEcdhPublicKey();
        assertTrue(bothHandshakeDone.await(5, TimeUnit.SECONDS), "Ca 2 ben phai hoan tat trao khoa ECDH trong 5s");

        long startNanos = System.nanoTime();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            peerA.send(EnvelopeType.MESSAGE, new MessagePayload("m-" + i, "peer-a", "noi dung " + i, i));
        }
        long sendElapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        // Doi them mot khoang hop ly sau khi gui xong de cac goi tin con dang
        // "tren duong" (localhost thuong rat nhanh, nhung khong ep buoc ngay lap
        // tuc) co co hoi toi noi day du truoc khi doc ket qua.
        boolean allArrivedInTime = allReceived.await(10, TimeUnit.SECONDS);
        long totalElapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("Gui " + MESSAGE_COUNT + " Envelope lien tiep mat " + sendElapsedMillis
                + "ms; nhan duoc " + receivedIds.size() + "/" + MESSAGE_COUNT
                + " trong tong " + totalElapsedMillis + "ms (allArrivedInTime=" + allArrivedInTime + ")");

        // Khong ep 100% (UDP von khong dam bao) - nhung ghi lai ro rang ti le
        // that su thay vi gia dinh, va bao dong neu mat mat qua lon (vd > 5%)
        // tren CHINH localhost, noi ly ra it xay ra tac nghen mang that.
        double deliveryRatio = receivedIds.size() / (double) MESSAGE_COUNT;
        assertTrue(deliveryRatio >= 0.95,
                "Ti le nhan duoc tren localhost qua thap (" + (deliveryRatio * 100) + "%) - "
                        + "cho thay mat mat dang ke ngay ca khong co tac nghen mang that, "
                        + "co the la dau hieu buffer nhan qua nho so voi toc do gui.");
    }
}
