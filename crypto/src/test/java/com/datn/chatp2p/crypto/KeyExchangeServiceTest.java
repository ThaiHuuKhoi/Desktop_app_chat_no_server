package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KeyExchangeServiceTest {

    @Test
    void twoPeersDeriveTheSameSharedSecret() {
        KeyPair alice = KeyExchangeService.generateKeyPair();
        KeyPair bob = KeyExchangeService.generateKeyPair();

        SecretKeySpec aliceView =
                KeyExchangeService.deriveSharedSecret(alice.getPrivate(), bob.getPublic());
        SecretKeySpec bobView =
                KeyExchangeService.deriveSharedSecret(bob.getPrivate(), alice.getPublic());

        assertArrayEquals(aliceView.getEncoded(), bobView.getEncoded(),
                "ECDH phai ra cung mot shared secret o ca hai phia");
    }

    @Test
    void decodedPublicKeyRoundTripsThroughX509Encoding() {
        KeyPair alice = KeyExchangeService.generateKeyPair();

        var decoded = KeyExchangeService.decodePublicKey(alice.getPublic().getEncoded());

        assertNotNull(decoded);
        assertArrayEquals(alice.getPublic().getEncoded(), decoded.getEncoded());
    }
}
