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

    @Test
    void decryptFailsWhenCiphertextIsTampered() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();
        byte[] ciphertext = AesGcmCipher.encrypt(key, "chuyen 100k cho Khoi".getBytes(StandardCharsets.UTF_8));

        // Sua 1 bit ngay sau IV (byte dau tien cua phan ciphertext that)
        ciphertext[12] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt(key, ciphertext),
                "GCM tag phai phat hien du lieu bi sua tren duong truyen");
    }

    @Test
    void encryptingSamePlaintextTwiceGivesDifferentOutputs() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();
        byte[] plaintext = "xin chao".getBytes(StandardCharsets.UTF_8);

        byte[] first = AesGcmCipher.encrypt(key, plaintext);
        byte[] second = AesGcmCipher.encrypt(key, plaintext);

        assertFalse(java.util.Arrays.equals(first, second),
                "Moi lan ma hoa phai dung IV ngau nhien moi");
    }

    @Test
    void decryptRejectsInputShorterThanIv() {
        SecretKey key = sharedKeyBetweenTwoDemoPeers();

        assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.decrypt(key, new byte[11]));
    }

    private static SecretKey sharedKeyBetweenTwoDemoPeers() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();
        return KeyExchangeService.deriveSharedSecret(alice.getPrivate(), bob.getPublic());
    }
}
