package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityKeyStoreProtectionTest {

    /** Bo bao ve gia (XOR) de test chay tren moi he dieu hanh; client that dung DPAPI. */
    private static final class XorProtector implements KeyProtector {
        private final byte key;

        XorProtector(int key) {
            this.key = (byte) key;
        }

        @Override
        public String name() {
            return "xor-test";
        }

        @Override
        public byte[] protect(byte[] secret) {
            byte[] out = secret.clone();
            for (int i = 0; i < out.length; i++) {
                out[i] ^= key;
            }
            return out;
        }

        @Override
        public byte[] unprotect(byte[] protectedSecret) {
            return protect(protectedSecret);
        }
    }

    @TempDir
    Path dir;

    @Test
    void privateKeyIsNotStoredInPlainTextWhenProtected() throws Exception {
        Path file = dir.resolve("identity.properties");
        KeyPair created = IdentityKeyStore.loadOrCreate(file, new XorProtector(0x5A));

        String content = Files.readString(file);
        assertTrue(content.contains("version=2"));
        assertTrue(content.contains("protection=xor-test"));
        assertFalse(content.contains(Base64.getEncoder().encodeToString(created.getPrivate().getEncoded())),
                "Private key khong duoc nam tron trong file");

        KeyPair reloaded = IdentityKeyStore.loadOrCreate(file, new XorProtector(0x5A));
        assertArrayEquals(created.getPrivate().getEncoded(), reloaded.getPrivate().getEncoded());
    }

    @Test
    void oldPlainTextFileIsUpgradedAndKeepsTheSameIdentity() throws Exception {
        Path file = dir.resolve("identity.properties");
        KeyPair old = IdentityKeyStore.loadOrCreate(file); // version 1, de tron
        assertTrue(Files.readString(file).contains("version=1"));

        KeyPair upgraded = IdentityKeyStore.loadOrCreate(file, new XorProtector(0x33));

        assertArrayEquals(old.getPublic().getEncoded(), upgraded.getPublic().getEncoded(), "Danh tinh khong doi");
        String content = Files.readString(file);
        assertTrue(content.contains("version=2"));
        assertFalse(content.contains("privateKey="), "Truong private key de tron phai bien mat");
    }

    @Test
    void wrongProtectorOrNoProtectorCannotOpenAndFileIsKept() throws Exception {
        Path file = dir.resolve("identity.properties");
        IdentityKeyStore.loadOrCreate(file, new XorProtector(0x11));
        String before = Files.readString(file);

        assertThrows(IllegalStateException.class, () -> IdentityKeyStore.loadOrCreate(file, new XorProtector(0x22)),
                "Sai khoa giai ma (giong chep sang may khac) -> bao loi");
        assertThrows(IllegalStateException.class, () -> IdentityKeyStore.loadOrCreate(file),
                "Khong co bo giai ma -> bao loi, khong tu sinh khoa moi");
        assertTrue(before.equals(Files.readString(file)), "Khong duoc ghi de file danh tinh");
    }
}
