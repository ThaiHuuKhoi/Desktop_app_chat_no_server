package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentitySignatureServiceTest {

    private static final String CHALLENGE = IdentitySignatureService.challengeFor("an-toan-cho-4821", "alice-id");

    @Test
    void signatureMadeWithPrivateKeyVerifiesWithMatchingPublicKey() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();

        byte[] signature = IdentitySignatureService.sign(alice.getPrivate(), CHALLENGE);

        assertTrue(IdentitySignatureService.verify(alice.getPublic(), signature, CHALLENGE));
    }

    @Test
    void verifyFailsWhenMessageIsDifferent() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();
        byte[] signature = IdentitySignatureService.sign(alice.getPrivate(), CHALLENGE);

        String otherRoom = IdentitySignatureService.challengeFor("phong-khac-1234", "alice-id");

        assertFalse(IdentitySignatureService.verify(alice.getPublic(), signature, otherRoom),
                "Chu ky cho phong nay khong duoc dung lai o phong khac");
    }

    @Test
    void verifyFailsWithAnotherPeersPublicKey() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();
        KeyPair mallory = IdentitySignatureService.generateIdentityKeyPair();
        byte[] signature = IdentitySignatureService.sign(alice.getPrivate(), CHALLENGE);

        assertFalse(IdentitySignatureService.verify(mallory.getPublic(), signature, CHALLENGE),
                "Ke mao danh khong the dung public key cua minh de xac thuc chu ky cua Alice");
    }

    @Test
    void verifyFailsWhenSignatureIsTampered() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();
        byte[] signature = IdentitySignatureService.sign(alice.getPrivate(), CHALLENGE);

        signature[signature.length - 1] ^= 0x01;

        assertFalse(IdentitySignatureService.verify(alice.getPublic(), signature, CHALLENGE));
    }

    @Test
    void verifyReturnsFalseInsteadOfThrowingForMalformedSignature() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();

        assertFalse(IdentitySignatureService.verify(alice.getPublic(), new byte[] {1, 2, 3}, CHALLENGE));
    }

    @Test
    void decodedPublicKeyStillVerifiesSignature() {
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();
        byte[] signature = IdentitySignatureService.sign(alice.getPrivate(), CHALLENGE);

        PublicKey decoded = IdentitySignatureService.decodePublicKey(alice.getPublic().getEncoded());

        assertTrue(IdentitySignatureService.verify(decoded, signature, CHALLENGE));
    }

    @Test
    void challengeFollowsRoomIdUnderscoreUserIdConvention() {
        assertEquals("room-1_user-1", IdentitySignatureService.challengeFor("room-1", "user-1"));
    }
}
