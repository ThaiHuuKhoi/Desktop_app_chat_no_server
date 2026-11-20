package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityBackupTest {

    @TempDir
    Path dir;

    @Test
    void exportThenImportWithTheRightPasswordGivesTheSameIdentity() {
        KeyPair original = IdentitySignatureService.generateIdentityKeyPair();
        byte[] backup = IdentityBackup.export(original, "mat-khau-dai".toCharArray());

        assertFalse(new String(backup, StandardCharsets.UTF_8)
                .contains(Base64.getEncoder().encodeToString(original.getPrivate().getEncoded())),
                "Private key khong duoc nam tron trong file sao luu");

        KeyPair restored = IdentityBackup.importBackup(backup, "mat-khau-dai".toCharArray());
        assertArrayEquals(original.getPublic().getEncoded(), restored.getPublic().getEncoded());
        assertArrayEquals(original.getPrivate().getEncoded(), restored.getPrivate().getEncoded());
    }

    @Test
    void wrongPasswordTamperingAndWeakPasswordsAreRejected() {
        KeyPair original = IdentitySignatureService.generateIdentityKeyPair();
        byte[] backup = IdentityBackup.export(original, "mat-khau-dai".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> IdentityBackup.importBackup(backup, "sai-mat-khau".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> IdentityBackup.importBackup("rac".getBytes(), "x".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> IdentityBackup.export(original, "ngan".toCharArray()));
    }

    @Test
    void replaceKeepsTheOldIdentityAsABackup() throws Exception {
        Path file = dir.resolve("identity.properties");
        KeyPair old = IdentityKeyStore.loadOrCreate(file);
        KeyPair imported = IdentitySignatureService.generateIdentityKeyPair();

        Path backupOfOld = IdentityKeyStore.replace(file, imported, null);

        assertNotNull(backupOfOld);
        assertTrue(Files.exists(backupOfOld));
        assertArrayEquals(imported.getPublic().getEncoded(), IdentityKeyStore.loadOrCreate(file).getPublic().getEncoded());
        assertArrayEquals(old.getPublic().getEncoded(), IdentityKeyStore.loadOrCreate(backupOfOld).getPublic().getEncoded());
    }
}
