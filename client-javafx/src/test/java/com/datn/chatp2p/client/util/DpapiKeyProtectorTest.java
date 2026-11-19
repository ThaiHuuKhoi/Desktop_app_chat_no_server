package com.datn.chatp2p.client.util;

import com.datn.chatp2p.crypto.KeyProtector;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Chay DPAPI that - chi tren Windows. */
class DpapiKeyProtectorTest {

    @Test
    void roundTripsThroughRealDpapiOnWindows() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
        KeyProtector protector = DpapiKeyProtector.forCurrentPlatform();
        assertNotNull(protector, "DPAPI phai dung duoc tren Windows");

        byte[] secret = "khoa-bi-mat-danh-tinh".getBytes();
        byte[] protectedBytes = protector.protect(secret);

        assertFalse(new String(protectedBytes).contains("khoa-bi-mat"), "Du lieu da duoc ma hoa");
        assertArrayEquals(secret, protector.unprotect(protectedBytes));
    }
}
