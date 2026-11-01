package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.IdentityPayload;
import com.datn.chatp2p.crypto.Fingerprint;
import com.datn.chatp2p.crypto.IdentityKeyStore;
import com.datn.chatp2p.crypto.IdentitySignatureService;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;

/** Owns the long-lived identity key and peer identity verification. */
public final class PeerIdentityService {

    public PeerIdentityService() {
    }

    private final KeyPair keyPair = IdentityKeyStore.loadOrCreate(
            Path.of(System.getProperty("user.home"), ".chat-p2p-java", "identity.properties"));
    private final String localFingerprint = Fingerprint.of(keyPair.getPublic());

    String localFingerprint() {
        return localFingerprint;
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    IdentityPayload createPayload(String roomId, String userId) {
        return new IdentityPayload(
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(IdentitySignatureService.sign(
                        keyPair.getPrivate(),
                        IdentitySignatureService.challengeFor(roomId, userId))));
    }

    Verification verify(IdentityPayload payload, String roomId, String peerId) {
        PublicKey publicKey = IdentitySignatureService.decodePublicKey(
                Base64.getDecoder().decode(payload.publicKey()));
        boolean valid = IdentitySignatureService.verify(
                publicKey,
                Base64.getDecoder().decode(payload.signature()),
                IdentitySignatureService.challengeFor(roomId, peerId));
        return new Verification(valid, Fingerprint.of(publicKey));
    }

    record Verification(boolean valid, String fingerprint) {
    }
}
