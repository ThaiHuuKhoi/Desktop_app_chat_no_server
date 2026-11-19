package com.datn.chatp2p.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustedPeersTest {

    private static final String FP = "A1B2 C3D4 E5F6 0718";

    @TempDir
    Path dir;

    @Test
    void unknownUntilTrustedThenRememberedAcrossRestarts() {
        Path file = dir.resolve("trusted-peers.properties");
        TrustedPeers peers = new TrustedPeers(file);
        assertEquals(TrustedPeers.Status.UNKNOWN, peers.status("An", FP));

        peers.trust("An", FP);

        TrustedPeers reloaded = new TrustedPeers(file);
        assertEquals(TrustedPeers.Status.TRUSTED, reloaded.status("An", FP));
        assertEquals(TrustedPeers.Status.TRUSTED, reloaded.status("  an ", "a1b2c3d4e5f60718"),
                "Khong phan biet hoa thuong / khoang trang");
    }

    @Test
    void differentFingerprintForSameNameIsChanged() {
        TrustedPeers peers = new TrustedPeers(dir.resolve("t.properties"));
        peers.trust("An", FP);

        assertEquals(TrustedPeers.Status.CHANGED, peers.status("An", "FFFF EEEE DDDD CCCC"));
        assertEquals(TrustedPeers.Status.UNKNOWN, peers.status("Binh", FP));
    }

    @Test
    void forgetRemovesTrust() {
        TrustedPeers peers = new TrustedPeers(dir.resolve("t.properties"));
        peers.trust("An", FP);
        peers.forget("An");

        assertEquals(TrustedPeers.Status.UNKNOWN, peers.status("An", FP));
    }
}
