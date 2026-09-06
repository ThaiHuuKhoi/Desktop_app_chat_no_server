package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.protocol.EnvelopeCodec;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Boc 1 {@link DataChannel} da mo voi DUNG 1 peer, tu lo viec trao khoa phien
 * bang ECDH ngay khi vua tao xong, roi dung khoa do de goi/mo {@link Envelope}
 * qua {@link EnvelopeCodec} - Tai-lieu-ky-thuat.md Phan E.4.
 *
 * <p><b>Vong doi trao khoa</b> (ca 2 phia doi xung, khong ai "hoi truoc"):
 * <ol>
 *   <li>Ben tao {@code PeerConnection} (sau khi ICE xong, xem
 *       {@code IceP2pConnectionEstablisher.onConnected}) phai tu goi
 *       {@link #sendEcdhPublicKey()} ngay - day la goi tin DUY NHAT di qua
 *       kenh nay o dang KHONG ma hoa (ban than public key khong can giu bi mat).</li>
 *   <li>{@link #handleIncoming} coi goi tin DAU TIEN nhan duoc la public key
 *       ECDH cua doi phuong (chua co {@code codec} nghia la chua xong handshake)
 *       - tu dong tinh {@code deriveSharedSecret}, dung khoa AES do de tao
 *       {@link EnvelopeCodec}, roi goi {@code onHandshakeComplete}.</li>
 *   <li>Tu goi tin thu 2 tro di, moi thu deu di qua {@code EnvelopeCodec}
 *       (da ma hoa AES-GCM) - dung nguyen tac E.1 muc 1: khong co DTLS nhu
 *       WebRTC nen phai tu ma hoa moi byte, TRU dung goi tin trao khoa dau tien.</li>
 * </ol>
 *
 * <p><b>Chiu loi khi mat goi UDP</b> (Tai-lieu-ky-thuat.md Phan H.3 - UDP khong
 * dam bao thu tu/khong mat goi): {@code P2pDataChannel} khong co ACK/retry o
 * tang duoi, nen goi public key ECDH ban dau (chi gui DUNG 1 lan) hoan toan co
 * the bi mat tren duong truyen - da xac nhan THAT bang test thuc te (mesh 3
 * peer thinh thoang "ICE hoan tat nhung handshake ECDH khong bao gio xong" -
 * ~50% ti le that bai qua vai lan chay lien tiep tren cung 1 may). {@link #sendEcdhPublicKey()}
 * vi vay tu dong GUI LAI toi da {@value #HANDSHAKE_RETRY_ATTEMPTS} lan, cach
 * nhau {@value #HANDSHAKE_RETRY_INTERVAL_MILLIS}ms - an toan de gui lai du
 * doi phuong co the DA hoan tat handshake tu ban sao truoc do (ban sao sau se
 * bi coi la du lieu ma hoa, giai ma that bai, bi {@code P2pDataChannel} bo
 * qua an toan thay vi lam sap ket noi - xem javadoc lop do).
 *
 * <p><b>Chua lam</b> (de bo sung khi co {@code IdentitySignatureService} o
 * module crypto): tu dong gui/xac thuc {@code EnvelopeType.PEER_IDENTITY}
 * ngay sau khi handshake xong - hien tai {@link #verificationState} luon la
 * {@code UNVERIFIED} cho toi khi lop goi no (RoomSession) tu cap nhat.
 */
public final class PeerConnection {

    /** So lan GUI LAI (khong tinh lan dau) neu ben kia chua xac nhan handshake xong - xem javadoc lop nay. */
    static final int HANDSHAKE_RETRY_ATTEMPTS = 5;
    /** Khoang cach giua 2 lan gui lai public key ECDH. */
    static final long HANDSHAKE_RETRY_INTERVAL_MILLIS = 300;

    /**
     * Dung CHUNG cho MOI {@code PeerConnection} (khong phai 1 executor rieng
     * moi instance) - viec gui lai chi la 1 tac vu ngan han, thua thai neu tao
     * hang chuc thread rieng cho mesh nhieu peer.
     */
    private static final ScheduledExecutorService HANDSHAKE_RETRY_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "peer-connection-ecdh-retry");
        thread.setDaemon(true);
        return thread;
    });

    private final String peerId;
    private final DataChannel dataChannel;
    private final KeyPair ecdhKeyPair;
    private final BiConsumer<PeerConnection, Envelope> onEnvelopeReceived;
    private final Runnable onHandshakeComplete;

    /** null cho toi khi handshake ECDH xong - xem javadoc lop nay. */
    private volatile EnvelopeCodec codec;
    private volatile PeerVerificationState verificationState = PeerVerificationState.UNVERIFIED;
    private volatile String customUsername;
    private volatile ScheduledFuture<?> handshakeRetryTask;

    /**
     * @param peerId              id on dinh cua peer ben kia (khong doi trong suot phien).
     * @param dataChannel         kenh da mo (thuc te la {@code P2pDataChannel} that,
     *                            hoac 1 dau cua {@code LoopbackDataChannel.Pair} khi test).
     * @param ecdhKeyPair         cap khoa ECDH TAM THOI sinh RIENG cho ket noi voi peer
     *                            nay ({@code KeyExchangeService.generateKeyPair()}) - khac
     *                            voi cap khoa danh tinh lau dai (chua co, se la ECDSA).
     * @param onEnvelopeReceived  goi moi khi nhan duoc 1 Envelope da giai ma xong (SAU
     *                            khi handshake hoan tat) - tham so dau la chinh
     *                            {@code PeerConnection} nay, de lop goi biet Envelope
     *                            den tu peer nao ma khong can tu luu map rieng.
     * @param onHandshakeComplete goi DUNG 1 LAN, ngay khi trao khoa ECDH xong va
     *                            {@link #send} bat dau dung duoc - null neu khong can biet.
     */
    public PeerConnection(
            String peerId,
            DataChannel dataChannel,
            KeyPair ecdhKeyPair,
            BiConsumer<PeerConnection, Envelope> onEnvelopeReceived,
            Runnable onHandshakeComplete) {
        this.peerId = Objects.requireNonNull(peerId, "peerId");
        this.dataChannel = Objects.requireNonNull(dataChannel, "dataChannel");
        this.ecdhKeyPair = Objects.requireNonNull(ecdhKeyPair, "ecdhKeyPair");
        this.onEnvelopeReceived = Objects.requireNonNull(onEnvelopeReceived, "onEnvelopeReceived");
        this.onHandshakeComplete = onHandshakeComplete;
        dataChannel.onReceive(this::handleIncoming);
    }

    /**
     * Phai duoc goi ngay sau khi tao xong {@code PeerConnection} (ca 2 phia) -
     * gui public key ECDH cua minh, CHUA ma hoa (xem javadoc lop nay, buoc 1).
     * Tu dong gui lai toi da {@link #HANDSHAKE_RETRY_ATTEMPTS} lan (xem javadoc
     * lop nay muc "Chiu loi khi mat goi UDP") - khong can goi lai tu ben ngoai.
     */
    public void sendEcdhPublicKey() {
        byte[] publicKeyEncoded = ecdhKeyPair.getPublic().getEncoded();
        dataChannel.send(publicKeyEncoded);

        AtomicInteger remainingRetries = new AtomicInteger(HANDSHAKE_RETRY_ATTEMPTS);
        handshakeRetryTask = HANDSHAKE_RETRY_EXECUTOR.scheduleAtFixedRate(() -> {
            // Nem loi khi het luot (thay vi tu goi cancel()) de tan dung dung
            // hanh vi co san cua scheduleAtFixedRate: 1 task nem loi se tu dong
            // KHONG duoc lich lai nua, khong can tu quan ly co (flag) rieng.
            if (remainingRetries.getAndDecrement() <= 0) {
                throw new IllegalStateException("Da het luot gui lai public key ECDH cho peer " + peerId);
            }
            // dataChannel.send() cung co the tu nem loi (vd channel da dong vi
            // peer roi phong) - cung se tu dung lich lai theo dung co che tren.
            dataChannel.send(publicKeyEncoded);
        }, HANDSHAKE_RETRY_INTERVAL_MILLIS, HANDSHAKE_RETRY_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** {@code true} khi da trao khoa xong va {@link #send} dung duoc. */
    public boolean isHandshakeComplete() {
        return codec != null;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getCustomUsername() {
        return customUsername;
    }

    public void setCustomUsername(String customUsername) {
        this.customUsername = customUsername;
    }

    public PeerVerificationState getVerificationState() {
        return verificationState;
    }

    public void setVerificationState(PeerVerificationState verificationState) {
        this.verificationState = Objects.requireNonNull(verificationState, "verificationState");
    }

    /** Ma hoa {@code payload} thanh 1 Envelope roi gui qua {@link #dataChannel}. */
    public <T> void send(EnvelopeType type, String namespace, T payload) {
        dataChannel.send(requireCodec().encode(type, namespace, payload));
    }

    /** Nhu {@link #send(EnvelopeType, String, Object)} nhung khong co namespace. */
    public <T> void send(EnvelopeType type, T payload) {
        send(type, null, payload);
    }

    public void close() {
        ScheduledFuture<?> task = handshakeRetryTask;
        if (task != null) {
            task.cancel(false);
        }
        dataChannel.close();
    }

    private void handleIncoming(byte[] data) {
        if (codec == null) {
            completeHandshake(data);
            return;
        }
        Envelope envelope = codec.decode(data);
        onEnvelopeReceived.accept(this, envelope);
    }

    private void completeHandshake(byte[] peerPublicKeyEncoded) {
        PublicKey peerPublicKey = KeyExchangeService.decodePublicKey(peerPublicKeyEncoded);
        SecretKey sessionKey = KeyExchangeService.deriveSharedSecret(ecdhKeyPair.getPrivate(), peerPublicKey);
        this.codec = new EnvelopeCodec(sessionKey);
        if (onHandshakeComplete != null) {
            onHandshakeComplete.run();
        }
    }

    private EnvelopeCodec requireCodec() {
        EnvelopeCodec c = this.codec;
        if (c == null) {
            throw new IllegalStateException(
                    "Chua hoan tat trao khoa ECDH voi peer " + peerId
                            + " - chi goi send() sau khi onHandshakeComplete da chay");
        }
        return c;
    }
}
