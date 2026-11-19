package com.datn.chatp2p.p2p.protocol;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.crypto.AesGcmCipher;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Diem noi giua {@code crypto} (da co {@link AesGcmCipher}) va {@code common}
 * (model + Jackson) o tang {@code p2p-core} - Tai-lieu-ky-thuat.md Phan E.3.4.
 * Day la lop DUY NHAT trong toan he thong biet ca 2 viec: serialize JSON va
 * ma hoa AES-GCM cho {@link Envelope} - moi noi khac (vd {@code PeerConnection})
 * chi goi {@link #encode}/{@link #decode}, khong tu dung {@code AesGcmCipher}
 * truc tiep.
 *
 * <p><b>Khoa AES dung o day la khoa PHIEN VOI DUNG 1 PEER</b> (sinh tu ECDH
 * luc thiet lap ket noi, xem {@code PeerConnection}) - moi cap peer trong 1
 * phong co 1 {@code EnvelopeCodec} rieng voi khoa rieng, khong dung chung.
 *
 * <p><b>Dinh dang goi</b> (12 byte dau la nonce AES-GCM):
 * {@code so thu tu (8 byte, big-endian) || 4 byte ngau nhien || ciphertext+tag}.
 * <ul>
 *   <li>Moi chieu 1 khoa ({@link KeyExchangeService.SessionKeys}) va so thu tu tang dan tu 0, nen
 *       (khoa, nonce) khong bao gio lap lai.</li>
 *   <li>So thu tu nam trong nonce nen duoc GCM xac thuc: sua so thu tu la giai ma that bai.</li>
 *   <li>Ben nhan chan goi trung / qua cu bang {@link ReplayWindow} ({@link ReplayedPacketException}).</li>
 * </ul>
 */
public final class EnvelopeCodec {

    private static final int SEQUENCE_BYTES = 8;
    private static final int NONCE_BYTES = 12;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretKey sendKey;
    private final SecretKey receiveKey;
    private final java.util.concurrent.atomic.AtomicLong nextSendSequence = new java.util.concurrent.atomic.AtomicLong();
    private final ReplayWindow replayWindow = new ReplayWindow();

    /** Codec cua 1 ket noi that: khoa gui va khoa nhan rieng (HKDF, xem {@link KeyExchangeService#deriveSessionKeys}). */
    public EnvelopeCodec(KeyExchangeService.SessionKeys keys) {
        this.sendKey = keys.sendKey();
        this.receiveKey = keys.receiveKey();
    }

    /**
     * 1 khoa cho ca 2 chieu - chi dung trong test (ma hoa roi tu giai ma lai). 4 byte ngau nhien
     * trong nonce giu cho 2 codec dung chung khoa gan nhu khong bao gio trung nonce.
     */
    public EnvelopeCodec(SecretKey sessionKey) {
        this.sendKey = sessionKey;
        this.receiveKey = sessionKey;
    }

    /**
     * {@code payload} -> JSON -> {@link Envelope} -> JSON -> AES-GCM -> byte[]
     * san sang cho {@code DataChannel.send()}.
     */
    public <T> byte[] encode(EnvelopeType type, String namespace, T payload) {
        try {
            byte[] payloadJson = objectMapper.writeValueAsBytes(payload);
            Envelope envelope = new Envelope(type, namespace, System.currentTimeMillis(), payloadJson);
            byte[] envelopeJson = objectMapper.writeValueAsBytes(envelope);
            long sequence = nextSendSequence.getAndIncrement();
            byte[] nonce = new byte[NONCE_BYTES];
            java.nio.ByteBuffer.wrap(nonce).putLong(sequence);
            byte[] random = new byte[NONCE_BYTES - SEQUENCE_BYTES];
            RANDOM.nextBytes(random);
            System.arraycopy(random, 0, nonce, SEQUENCE_BYTES, random.length);
            byte[] ciphertext = AesGcmCipher.encryptWithNonce(sendKey, nonce, envelopeJson);
            byte[] packet = new byte[NONCE_BYTES + ciphertext.length];
            System.arraycopy(nonce, 0, packet, 0, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, packet, NONCE_BYTES, ciphertext.length);
            return packet;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Khong serialize duoc payload cho EnvelopeType " + type, e);
        }
    }

    /** Nhu {@link #encode(EnvelopeType, String, Object)} nhung khong co namespace (namespace=null). */
    public <T> byte[] encode(EnvelopeType type, T payload) {
        return encode(type, null, payload);
    }

    /**
     * byte[] nhan tu {@code DataChannel.onReceive} -> AES-GCM decrypt -> JSON
     * -> {@link Envelope} (payload ben trong VAN o dang byte[] JSON, chua
     * parse thanh record cu the - goi tiep {@link #parsePayload} voi dung
     * {@code Class} tuong ung {@code envelope.type()}).
     *
     * @throws IllegalStateException neu giai ma that bai (sai khoa hoac du
     *                                lieu bi thay doi tren duong truyen) -
     *                                day co the la tin hieu tan cong, xem
     *                                Tai-lieu-ky-thuat.md Phan H.1.
     * @throws ReplayedPacketException neu so thu tu da nhan roi hoac qua cu (phat lai / nhan doi).
     */
    public Envelope decode(byte[] raw) {
        if (raw.length <= NONCE_BYTES) {
            throw new IllegalArgumentException("Goi qua ngan");
        }
        byte[] nonce = java.util.Arrays.copyOfRange(raw, 0, NONCE_BYTES);
        long sequence = java.nio.ByteBuffer.wrap(nonce).getLong();
        byte[] envelopeJson;
        // Kiem tra + giai ma + danh dau trong 1 khoi: 2 ban sao cung goi toi cung luc tren 2 thread
        // khong the cung lot qua. Chi danh dau SAU khi giai ma thanh cong (xem ReplayWindow).
        synchronized (replayWindow) {
            if (!replayWindow.isFresh(sequence)) {
                throw new ReplayedPacketException(sequence);
            }
            envelopeJson = AesGcmCipher.decryptWithNonce(receiveKey, nonce,
                    java.util.Arrays.copyOfRange(raw, NONCE_BYTES, raw.length));
            replayWindow.markSeen(sequence);
        }
        try {
            return objectMapper.readValue(envelopeJson, Envelope.class);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Du lieu nhan duoc sau khi giai ma khong phai Envelope hop le", e);
        }
    }

    /** {@code envelope.payload()} (JSON da decode nhung CHUA parse) -> {@code T} cu the. */
    public <T> T parsePayload(Envelope envelope, Class<T> payloadType) {
        try {
            return objectMapper.readValue(envelope.payload(), payloadType);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Khong parse duoc payload cua Envelope thanh " + payloadType.getSimpleName(), e);
        }
    }
}
