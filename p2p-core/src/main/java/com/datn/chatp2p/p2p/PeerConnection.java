package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.protocol.EnvelopeCodec;
import com.datn.chatp2p.p2p.protocol.ReplayedPacketException;

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
 *       - tu dong tao cap khoa phien (HKDF, moi chieu 1 khoa - {@code deriveSessionKeys}) de tao
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
 * <p><b>Chong tan cong REPLAY</b> (phat hien khi ra soat bao mat Tang 4, so
 * sanh voi tham khao chitchatter - {@code src/services/Encryption/Encryption.ts}
 * cua ho KHONG he co logic ma hoa/chong replay noi dung chat, vi WebRTC
 * DataChannel that cua trinh duyet da bat buoc dung DTLS ben duoi - DTLS tu
 * dong chong replay bang sequence number + sliding window ngay trong giao
 * thuc (RFC 6347 Muc 4.1.2.6), "mien phi" cho ung dung. Lop nay KHONG co DTLS
 * (xem muc "Vong doi trao khoa" o tren) nen mat luon bao ve do - da xac nhan
 * THAT bang test: gui lai y nguyen 1 ban ma hoa da gui thanh cong truoc do
 * (mo phong ke tan cong bat duoc goi tin tren duong truyen roi phat lai) VAN
 * duoc xu ly thanh cong lan 2 neu khong co gi ngan. Vi vay moi goi mang 1 SO
 * THU TU (nam trong nonce AES-GCM nen duoc xac thuc) va {@link EnvelopeCodec}
 * giu 1 cua so truot {@value #REPLAY_WINDOW_SIZE} so nhu DTLS: goi trung hoac
 * cu hon cua so bi bo ({@link ReplayedPacketException}); goi den SAI THU TU
 * nhung con trong cua so van nhan (UDP co the dao thu tu).
 *
 * <p><b>Khoa phien</b>: {@code KeyExchangeService.deriveSessionKeys} dung HKDF-SHA256 tach
 * 2 khoa rieng cho 2 chieu gui, gan voi dung 2 public key ECDH cua phien.
 *
 * <p><b>Gan danh tinh voi phien ECDH</b>: goi tin ECDH dau tien KHONG duoc xac
 * thuc, nen lop nay luu lai public key ECDH cua ca 2 ben
 * ({@link #getLocalEcdhPublicKey()}, {@link #getRemoteEcdhPublicKey()}). Tang tren
 * (client) dua 2 khoa nay vao chu ky {@code PEER_IDENTITY}
 * ({@code IdentitySignatureService.handshakeChallengeFor}) - ke MITM tu thay khoa
 * ECDH se lam chu ky danh tinh that bai. {@link #verificationState} do tang tren cap nhat.
 */
public final class PeerConnection {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(PeerConnection.class.getName());

    /** So lan GUI LAI (khong tinh lan dau) neu ben kia chua xac nhan handshake xong - xem javadoc lop nay. */
    static final int HANDSHAKE_RETRY_ATTEMPTS = 5;
    /** Khoang cach giua 2 lan gui lai public key ECDH. */
    static final long HANDSHAKE_RETRY_INTERVAL_MILLIS = 300;

    /** Do rong cua so chong replay theo so thu tu goi (bang {@code ReplayWindow.SIZE} trong EnvelopeCodec). */
    static final int REPLAY_WINDOW_SIZE = 1024;

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
    /** Public key ECDH (X.509) cua doi phuong - null cho toi khi handshake xong. */
    private volatile byte[] remoteEcdhPublicKey;
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
        LOG.info(() -> "Gui public key ECDH toi peer " + shortId(peerId) + " (bat dau trao khoa)");
        dataChannel.send(publicKeyEncoded);

        AtomicInteger remainingRetries = new AtomicInteger(HANDSHAKE_RETRY_ATTEMPTS);
        handshakeRetryTask = HANDSHAKE_RETRY_EXECUTOR.scheduleAtFixedRate(() -> {
            // Nem loi khi het luot (thay vi tu goi cancel()) de tan dung dung
            // hanh vi co san cua scheduleAtFixedRate: 1 task nem loi se tu dong
            // KHONG duoc lich lai nua, khong can tu quan ly co (flag) rieng.
            if (remainingRetries.getAndDecrement() <= 0) {
                if (codec == null) {
                    LOG.warning(() -> "Het " + HANDSHAKE_RETRY_ATTEMPTS + " lan gui lai public key ECDH ma chua nhan duoc khoa cua peer "
                            + shortId(peerId) + " (mat goi UDP?)");
                }
                throw new IllegalStateException("Da het luot gui lai public key ECDH cho peer " + peerId);
            }
            LOG.fine(() -> "Gui lai public key ECDH toi peer " + shortId(peerId));
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

    /** Public key ECDH (X.509) cua MINH trong phien nay. */
    public byte[] getLocalEcdhPublicKey() {
        return ecdhKeyPair.getPublic().getEncoded();
    }

    /** Public key ECDH (X.509) cua doi phuong trong phien nay, hoac null neu chua handshake xong. */
    public byte[] getRemoteEcdhPublicKey() {
        byte[] key = remoteEcdhPublicKey;
        return key == null ? null : key.clone();
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
        // Chong replay - xem javadoc lop nay muc "Chong tan cong REPLAY". Chi ap
        // dung SAU khi handshake xong (khong ap dung cho chinh goi cong khai ECDH
        // dau tien, vi ban sao GUI LAI cua no - co y, xem sendEcdhPublicKey - deu
        // toi day qua nhanh else nay, tu bi tu choi neu trung van tay, dung y
        // muon: khong can xu ly lai nhung ban sao handshake du thua).
        Envelope envelope;
        try {
            envelope = codec.decode(data);
        } catch (ReplayedPacketException e) {
            LOG.fine(() -> "Bo qua goi trung / qua cu (replay / nhan doi) tu peer " + shortId(peerId));
            return;
        }
        onEnvelopeReceived.accept(this, envelope);
    }

    private static String shortId(String id) {
        return id.substring(0, Math.min(8, id.length()));
    }

    private void completeHandshake(byte[] peerPublicKeyEncoded) {
        PublicKey peerPublicKey = KeyExchangeService.decodePublicKey(peerPublicKeyEncoded);
        KeyExchangeService.SessionKeys keys = KeyExchangeService.deriveSessionKeys(ecdhKeyPair, peerPublicKey);
        this.remoteEcdhPublicKey = peerPublicKeyEncoded.clone();
        this.codec = new EnvelopeCodec(keys);
        LOG.info(() -> "Trao khoa ECDH xong voi peer " + shortId(peerId)
                + " - tu day moi goi duoc ma hoa AES-256-GCM (HKDF, khoa rieng moi chieu, co so thu tu goi)");
        if (com.datn.chatp2p.common.log.SecretLogging.enabled()) {
            LOG.info(() -> "  [bi mat] khoa GUI AES-256 toi peer " + shortId(peerId) + " = "
                    + com.datn.chatp2p.common.log.SecretLogging.revealHex(keys.sendKey().getEncoded()));
            LOG.info(() -> "  [bi mat] khoa NHAN AES-256 tu peer " + shortId(peerId) + " = "
                    + com.datn.chatp2p.common.log.SecretLogging.revealHex(keys.receiveKey().getEncoded()));
            LOG.info(() -> "  [bi mat] public key ECDH cua minh = "
                    + com.datn.chatp2p.common.log.SecretLogging.revealHex(ecdhKeyPair.getPublic().getEncoded()));
            LOG.info(() -> "  [bi mat] public key ECDH cua peer = "
                    + com.datn.chatp2p.common.log.SecretLogging.revealHex(peerPublicKeyEncoded));
        }
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
