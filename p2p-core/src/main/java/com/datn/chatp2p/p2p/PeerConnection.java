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
 * <p><b>Chua lam</b> (de bo sung khi co {@code IdentitySignatureService} o
 * module crypto): tu dong gui/xac thuc {@code EnvelopeType.PEER_IDENTITY}
 * ngay sau khi handshake xong - hien tai {@link #verificationState} luon la
 * {@code UNVERIFIED} cho toi khi lop goi no (RoomSession) tu cap nhat.
 */
public final class PeerConnection {

    private final String peerId;
    private final DataChannel dataChannel;
    private final KeyPair ecdhKeyPair;
    private final BiConsumer<PeerConnection, Envelope> onEnvelopeReceived;
    private final Runnable onHandshakeComplete;

    /** null cho toi khi handshake ECDH xong - xem javadoc lop nay. */
    private volatile EnvelopeCodec codec;
    private volatile PeerVerificationState verificationState = PeerVerificationState.UNVERIFIED;
    private volatile String customUsername;

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
     */
    public void sendEcdhPublicKey() {
        dataChannel.send(ecdhKeyPair.getPublic().getEncoded());
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
