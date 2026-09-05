package com.datn.chatp2p.p2p.protocol;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiem tra {@link EnvelopeCodec} bang khoa AES sinh that qua ECDH cua module
 * {@code crypto} (khong mock) - dung theo dung cach EnvelopeCodec se duoc
 * dung that boi {@code PeerConnection}.
 */
class EnvelopeCodecTest {

    private SecretKey deriveSharedKey() {
        KeyPair a = KeyExchangeService.generateKeyPair();
        KeyPair b = KeyExchangeService.generateKeyPair();
        // Chi can 1 khoa dung chung cho ca encode/decode trong test nay -
        // deriveSharedSecret tu 2 phia ra cung 1 gia tri, lay tam 1 phia.
        return KeyExchangeService.deriveSharedSecret(a.getPrivate(), b.getPublic());
    }

    @Test
    void encodeThenDecodeRoundtripsPayloadCorrectly() {
        EnvelopeCodec codec = new EnvelopeCodec(deriveSharedKey());
        MessagePayload original = new MessagePayload("msg-1", "peer-a", "xin chao", 1_000L);

        byte[] wireBytes = codec.encode(EnvelopeType.MESSAGE, EnvelopeNamespace.GROUP, original);
        Envelope decoded = codec.decode(wireBytes);

        assertEquals(EnvelopeType.MESSAGE, decoded.type());
        assertEquals(EnvelopeNamespace.GROUP, decoded.namespace());

        MessagePayload roundtripped = codec.parsePayload(decoded, MessagePayload.class);
        assertEquals(original, roundtripped);
    }

    @Test
    void wireBytesAreNotPlaintextJson() {
        EnvelopeCodec codec = new EnvelopeCodec(deriveSharedKey());
        MessagePayload payload = new MessagePayload("msg-2", "peer-a", "noi dung bi mat", 2_000L);

        byte[] wireBytes = codec.encode(EnvelopeType.MESSAGE, payload);
        String asText = new String(wireBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertFalse(asText.contains("noi dung bi mat"), "Ciphertext khong duoc chua nguyen van plaintext");
    }

    @Test
    void decodingWithWrongKeyFails() {
        SecretKey correctKey = deriveSharedKey();
        SecretKey wrongKey = deriveSharedKey();

        EnvelopeCodec sender = new EnvelopeCodec(correctKey);
        EnvelopeCodec attacker = new EnvelopeCodec(wrongKey);

        byte[] wireBytes = sender.encode(EnvelopeType.MESSAGE, new MessagePayload("m", "a", "t", 0L));

        assertThrows(IllegalStateException.class, () -> attacker.decode(wireBytes));
    }

    @Test
    void decodingTamperedBytesFails() {
        EnvelopeCodec codec = new EnvelopeCodec(deriveSharedKey());
        byte[] wireBytes = codec.encode(EnvelopeType.MESSAGE, new MessagePayload("m", "a", "t", 0L));

        byte[] tampered = wireBytes.clone();
        tampered[tampered.length - 1] ^= 0x01; // lat 1 bit trong tag/ciphertext

        assertThrows(IllegalStateException.class, () -> codec.decode(tampered));
    }
}
