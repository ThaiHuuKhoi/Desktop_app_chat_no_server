package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FingerprintTest {

    @Test
    void sameKeyAlwaysProducesSameFingerprint() {
        KeyPair alice = KeyExchangeService.generateKeyPair();

        assertEquals(Fingerprint.of(alice.getPublic()), Fingerprint.of(alice.getPublic()));
    }

    @Test
    void differentKeysProduceDifferentFingerprints() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();

        assertNotEquals(Fingerprint.of(alice.getPublic()), Fingerprint.of(bob.getPublic()),
                "Hai khoa khac nhau phai cho van tay khac nhau, neu khong khong phat hien duoc MITM");
    }

    @Test
    void fingerprintIsSixteenGroupsOfFourUppercaseHexDigits() {
        KeyPair alice = KeyExchangeService.generateKeyPair();

        String fingerprint = Fingerprint.of(alice.getPublic());

        // SHA-256 = 32 byte = 64 ky tu hex = 16 nhom 4 ky tu
        assertTrue(fingerprint.matches("([0-9A-F]{4} ){15}[0-9A-F]{4}"),
                "Sai dinh dang fingerprint: " + fingerprint);
    }
}
