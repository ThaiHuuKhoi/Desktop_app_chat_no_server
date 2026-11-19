package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HkdfAndSessionKeysTest {

    private static final HexFormat HEX = HexFormat.of();

    /** RFC 5869, Appendix A.1 (Test Case 1). */
    @Test
    void hkdfMatchesRfc5869TestVector() {
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
        byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");

        byte[] prk = Hkdf.extract(salt, ikm);
        byte[] okm = Hkdf.expand(prk, info, 42);

        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", HEX.formatHex(prk));
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                HEX.formatHex(okm));
    }

    /** RFC 5869, Appendix A.3 (Test Case 3: salt va info rong). */
    @Test
    void hkdfWithEmptySaltAndInfoMatchesRfc5869() {
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");

        byte[] okm = Hkdf.derive(ikm, new byte[0], new byte[0], 42);

        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
                HEX.formatHex(okm));
    }

    @Test
    void bothSidesGetMirroredKeysAndDirectionsDiffer() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();

        KeyExchangeService.SessionKeys aliceKeys = KeyExchangeService.deriveSessionKeys(alice, bob.getPublic());
        KeyExchangeService.SessionKeys bobKeys = KeyExchangeService.deriveSessionKeys(bob, alice.getPublic());

        assertArrayEquals(aliceKeys.sendKey().getEncoded(), bobKeys.receiveKey().getEncoded(), "A gui = B nhan");
        assertArrayEquals(bobKeys.sendKey().getEncoded(), aliceKeys.receiveKey().getEncoded(), "B gui = A nhan");
        assertFalse(java.util.Arrays.equals(aliceKeys.sendKey().getEncoded(), aliceKeys.receiveKey().getEncoded()),
                "2 chieu phai dung 2 khoa khac nhau");
        assertEquals(32, aliceKeys.sendKey().getEncoded().length, "AES-256");
    }

    @Test
    void thirdPartyGetsDifferentKeys() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();
        KeyPair mallory = KeyExchangeService.generateKeyPair();

        byte[] real = KeyExchangeService.deriveSessionKeys(alice, bob.getPublic()).sendKey().getEncoded();
        byte[] withMallory = KeyExchangeService.deriveSessionKeys(alice, mallory.getPublic()).sendKey().getEncoded();

        assertFalse(java.util.Arrays.equals(real, withMallory));
    }

    @Test
    void nonceCipherDetectsTamperedNonce() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();
        var key = KeyExchangeService.deriveSessionKeys(alice, bob.getPublic()).sendKey();
        byte[] nonce = new byte[12];
        nonce[7] = 5;

        byte[] ciphertext = AesGcmCipher.encryptWithNonce(key, nonce, "xin chao".getBytes());
        assertArrayEquals("xin chao".getBytes(), AesGcmCipher.decryptWithNonce(key, nonce, ciphertext));

        byte[] tampered = nonce.clone();
        tampered[7] = 6; // vd ke tan cong doi so thu tu goi
        assertThrows(IllegalStateException.class, () -> AesGcmCipher.decryptWithNonce(key, tampered, ciphertext));
    }
}
