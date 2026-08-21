package com.datn.chatp2p.common.model;

import java.util.Objects;

/**
 * Thong tin ve mot nguoi tham gia phong chat (co the la chinh minh hoac doi phuong).
 *
 * <p>Phong theo interface {@code Peer} trong {@code models/chat.ts} cua chitchatter,
 * rut gon cho pham vi chat van ban + fingerprint (bo cac truong lien quan
 * video/audio vi ngoai pham vi de tai - xem De-cuong-Chat-P2P-Java.md muc 3).
 */
public final class Peer {

    private final String peerId;
    private String customUsername;
    private String publicKeyFingerprint;
    private PeerVerificationState verificationState;

    public Peer(String peerId, String customUsername) {
        this.peerId = Objects.requireNonNull(peerId, "peerId");
        this.customUsername = Objects.requireNonNull(customUsername, "customUsername");
        this.verificationState = PeerVerificationState.UNVERIFIED;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getCustomUsername() {
        return customUsername;
    }

    public void setCustomUsername(String customUsername) {
        this.customUsername = customUsername;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public PeerVerificationState getVerificationState() {
        return verificationState;
    }

    public void setVerificationState(PeerVerificationState verificationState) {
        this.verificationState = Objects.requireNonNull(verificationState, "verificationState");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer other)) return false;
        return peerId.equals(other.peerId);
    }

    @Override
    public int hashCode() {
        return peerId.hashCode();
    }

    @Override
    public String toString() {
        return "Peer{peerId=%s, customUsername=%s, verificationState=%s}"
                .formatted(peerId, customUsername, verificationState);
    }
}
