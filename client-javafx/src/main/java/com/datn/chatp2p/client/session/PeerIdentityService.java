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

    /** File khoa danh tinh cua nguoi dung. */
    public static Path identityFile() {
        return Path.of(System.getProperty("user.home"), ".chat-p2p-java", "identity.properties");
    }

    /** Doc (hoac tao) khoa danh tinh - Windows: private key ma hoa DPAPI, file cu tu chuyen sang. */
    public static KeyPair loadIdentity() {
        return IdentityKeyStore.loadOrCreate(identityFile(), com.datn.chatp2p.client.util.DpapiKeyProtector.forCurrentPlatform());
    }

    /** Thay danh tinh bang khoa nhap tu file sao luu; tra ve ban luu danh tinh cu (hoac null). */
    public static Path replaceIdentity(KeyPair keyPair) {
        return IdentityKeyStore.replace(identityFile(), keyPair, com.datn.chatp2p.client.util.DpapiKeyProtector.forCurrentPlatform());
    }

    private final KeyPair keyPair = loadIdentity();
    private final String localFingerprint = Fingerprint.of(keyPair.getPublic());

    /** Van tay khoa danh tinh cua may nay (hien o man hinh dang nhap, hop so van tay). */
    public String localFingerprint() {
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
