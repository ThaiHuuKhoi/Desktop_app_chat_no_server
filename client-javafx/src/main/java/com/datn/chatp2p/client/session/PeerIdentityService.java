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

    // Windows: private key duoc ma hoa bang DPAPI (gan voi tai khoan Windows); file cu tu chuyen sang.
    private final KeyPair keyPair = IdentityKeyStore.loadOrCreate(
            Path.of(System.getProperty("user.home"), ".chat-p2p-java", "identity.properties"),
            com.datn.chatp2p.client.util.DpapiKeyProtector.forCurrentPlatform());
    private final String localFingerprint = Fingerprint.of(keyPair.getPublic());

    String localFingerprint() {
        return localFingerprint;
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Ky danh tinh GAN VOI phien ECDH hien tai (chong MITM tren buoc trao khoa).
     *
     * @param localEcdhPublicKey  khoa ECDH cua minh trong phien voi peer nay.
     * @param remoteEcdhPublicKey khoa ECDH cua peer ma minh nhin thay.
     */
    IdentityPayload createPayload(String roomId, String selfPeerId,
                                  byte[] localEcdhPublicKey, byte[] remoteEcdhPublicKey) {
        return new IdentityPayload(
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(IdentitySignatureService.sign(
                        keyPair.getPrivate(),
                        IdentitySignatureService.handshakeChallengeFor(
                                roomId, selfPeerId, localEcdhPublicKey, remoteEcdhPublicKey))));
    }

    /**
     * Xac thuc danh tinh cua {@code peerId}. Ben ky la peer, nen thu tu khoa la
     * (khoa ECDH cua peer, khoa ECDH cua minh) - nguoc voi luc ky.
     */
    Verification verify(IdentityPayload payload, String roomId, String peerId,
                        byte[] remoteEcdhPublicKey, byte[] localEcdhPublicKey) {
        PublicKey publicKey = IdentitySignatureService.decodePublicKey(
                Base64.getDecoder().decode(payload.publicKey()));
        boolean valid = IdentitySignatureService.verify(
                publicKey,
                Base64.getDecoder().decode(payload.signature()),
                IdentitySignatureService.handshakeChallengeFor(
                        roomId, peerId, remoteEcdhPublicKey, localEcdhPublicKey));
        return new Verification(valid, Fingerprint.of(publicKey));
    }

    record Verification(boolean valid, String fingerprint) {
    }
}
