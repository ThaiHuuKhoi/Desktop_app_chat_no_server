package com.datn.chatp2p.p2p.protocol;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.crypto.AesGcmCipher;
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
 */
public final class EnvelopeCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretKey sessionKey;

    public EnvelopeCodec(SecretKey sessionKey) {
        this.sessionKey = sessionKey;
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
            return AesGcmCipher.encrypt(sessionKey, envelopeJson);
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
     */
    public Envelope decode(byte[] raw) {
        byte[] envelopeJson = AesGcmCipher.decrypt(sessionKey, raw);
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
