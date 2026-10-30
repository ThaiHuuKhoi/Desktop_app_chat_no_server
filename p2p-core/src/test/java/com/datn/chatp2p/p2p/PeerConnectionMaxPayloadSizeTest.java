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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan gioi han kich thuoc payload THUC TE cua {@link PeerConnection#send}
 * khi di qua kenh UDP THAT ({@link P2pDataChannel}, khong phai
 * {@link LoopbackDataChannel} - kenh gia lap khong co gioi han nay).
 *
 * <p><b>Ly do can test rieng, khong chi doc code:</b> {@code P2pDataChannel}
 * gioi han 65.507 byte (UDP IPv4 toi da) nhung day KHONG PHAI gioi han thuc
 * te cho payload ung dung - truoc khi toi duoc kenh, du lieu da di qua nhieu
 * lop dong goi lam PHINH TO kich thuoc:
 * <ol>
 *   <li>{@code MessagePayload} -> JSON (Jackson).</li>
 *   <li>JSON do bi nhet vao truong {@code Envelope.payload} kieu {@code byte[]} -
 *       Jackson MAC DINH serialize {@code byte[]} thanh chuoi BASE64 khi lam
 *       phan cua 1 object khac -> phinh to them ~33%.</li>
 *   <li>Ca {@code Envelope} (chua chuoi base64 do) lai serialize thanh JSON 1
 *       lan nua.</li>
 *   <li>JSON cuoi cung do di qua AES-GCM ({@code AesGcmCipher}) - them 12 byte
 *       IV + 16 byte tag co dinh.</li>
 *   <li>Cuoi cung {@code P2pDataChannel} them 4 byte length-prefix.</li>
 * </ol>
 * Gop lai, gioi han THUC TE cho {@code text} cua 1 {@code MessagePayload} chi
 * con khoang ~49KB - chua tung duoc xac nhan hay ghi lai o dau, du la con so
 * quan trong cho tinh nang chia nho file/media (FILE_CHUNK/MEDIA_FRAME) sau
 * nay.
 */
class PeerConnectionMaxPayloadSizeTest {

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
    void aClearlyOversizedPayloadFailsWithAnUnderstandableErrorInsteadOfHangingOrSilentlyTruncating() throws Exception {
        setUpConnectedPeers(envelope -> { });

        // 60.000 ky tu - VUOT QUA ca gioi han tho 65.507 byte cua UDP mot khi
        // cong them phinh to do JSON+base64+AES-GCM (thuc te gioi han con thap
        // hon nhieu, ~49.000 - xem javadoc lop nay) - phai nem loi RO RANG,
        // khong duoc treo vo thoi han hay im lang cat bot du lieu.
        String oversizedText = "x".repeat(60_000);
        MessagePayload oversized = new MessagePayload("m-big", "peer-a", oversizedText, 1L);

        // Truoc khi them kiem tra ro rang trong P2pDataChannel.send(): loi that
        // nem ra la UncheckedIOException voi thong diep chung chung "Gui du lieu
        // qua P2pDataChannel that bai" - khong noi gi ve nguyen nhan la payload
        // qua lon, phai tu dao sau vao getCause() moi biet. Sau khi sua, phai la
        // IllegalArgumentException voi thong diep NEU RO nguyen nhan + con so cu
        // the, khong can doan.
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> peerA.send(EnvelopeType.MESSAGE, oversized),
                "Payload qua lon phai nem IllegalArgumentException ro rang, khong phai IOException chung chung");
        assertTrue(failure.getMessage().contains("qua lon"),
                "Thong diep loi phai noi ro nguyen nhan la payload qua lon, khong duoc chung chung");
        System.out.println("Loi that khi gui payload 60.000 ky tu: " + failure.getMessage());
    }

    @Test
    void aPayloadUnderTheEffectiveLimitStillArrivesIntact() throws Exception {
        BlockingQueue<Envelope> bInbox = new ArrayBlockingQueue<>(10);
        setUpConnectedPeers(bInbox::add);

        // 40.000 ky tu - ro rang duoi ngong hieu qua ~49.000 da uoc tinh o
        // javadoc lop nay (con nhieu bien do an toan cho phan JSON/base64/AES-GCM
        // con lai) - phai gui/nhan nguyen ven, khong bi cat bot.
        String text = "y".repeat(40_000);
        MessagePayload payload = new MessagePayload("m-ok", "peer-a", text, 2L);

        peerA.send(EnvelopeType.MESSAGE, payload);

        Envelope received = bInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Payload 40.000 ky tu (duoi ngong hieu qua) phai toi noi trong 5s");
        MessagePayload parsed = decodeOnPeerB(received);
        assertEquals(payload, parsed, "Noi dung phai nguyen ven, khong bi cat bot giua duong");
    }

    private MessagePayload decodeOnPeerB(Envelope received) {
        return new EnvelopeCodec(KeyExchangeService.deriveSharedSecret(peerBPrivateKey, peerAPublicKey))
                .parsePayload(received, MessagePayload.class);
    }

    // Giu lai 2 khoa cong khai/rieng cua 2 ben CHI de test co the tu giai ma
    // lai payload nhan duoc (giong het cach PeerConnectionTest da lam) - ban
    // than PeerConnection da tu giai ma noi bo roi, day chi la buoc kiem tra
    // THEM tu phia test, khong lien quan gi logic san xuat.
    private PrivateKey peerBPrivateKey;
    private PublicKey peerAPublicKey;

    private void setUpConnectedPeers(Consumer<Envelope> onPeerBEnvelope) throws Exception {
        socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        InetSocketAddress addressA = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort());
        InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

        P2pDataChannel channelA = new P2pDataChannel(socketA, addressB);
        P2pDataChannel channelB = new P2pDataChannel(socketB, addressA);

        KeyPair keyPairA = KeyExchangeService.generateKeyPair();
        KeyPair keyPairB = KeyExchangeService.generateKeyPair();
        peerAPublicKey = keyPairA.getPublic();
        peerBPrivateKey = keyPairB.getPrivate();

        CountDownLatch bothHandshakeDone = new CountDownLatch(2);
        peerA = new PeerConnection("peer-b", channelA, keyPairA,
                (from, envelope) -> { }, bothHandshakeDone::countDown);
        peerB = new PeerConnection("peer-a", channelB, keyPairB,
                (from, envelope) -> onPeerBEnvelope.accept(envelope), bothHandshakeDone::countDown);

        peerA.sendEcdhPublicKey();
        peerB.sendEcdhPublicKey();
        assertTrue(bothHandshakeDone.await(5, TimeUnit.SECONDS), "Ca 2 ben phai hoan tat trao khoa ECDH trong 5s");
    }
}
