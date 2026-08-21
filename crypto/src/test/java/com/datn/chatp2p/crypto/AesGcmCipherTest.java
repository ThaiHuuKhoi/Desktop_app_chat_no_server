package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmCipherTest {

    @Test
    void encryptThenDecryptReturnsOriginalPlaintext() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();
        byte[] plaintext = "Xin chao tu Peer A!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AesGcmCipher.encrypt(key, plaintext);
        byte[] decrypted = AesGcmCipher.decrypt(key, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void ciphertextIsNotEqualToPlaintext() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();
        byte[] plaintext = "Noi dung nhay cam".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AesGcmCipher.encrypt(key, plaintext);

        assertFalse(java.util.Arrays.equals(plaintext, ciphertext),
                "Ciphertext khong duoc trung voi plaintext");
    }

    @Test
    void decryptWithWrongKeyFails() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();
        SecretKey wrongKey = sharedKeyBetweenTwoDemoPeers();
        byte[] ciphertext = AesGcmCipher.encrypt(key, "bi mat".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt(wrongKey, ciphertext));
    }

    private static SecretKey sharedKeyBetweenTwoDemoPeers() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();
        return KeyExchangeService.deriveSharedSecret(alice.getPrivate(), bob.getPublic());
    }
}
