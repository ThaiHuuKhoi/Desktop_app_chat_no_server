package com.datn.chatp2p.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityKeyStoreTest {

    private static final String CHALLENGE = IdentitySignatureService.challengeFor("an-toan-cho-4821", "alice-id");

    @TempDir
    Path tempDir;

    @Test
    void firstCallCreatesKeyFile() {
        Path file = tempDir.resolve("identity.properties");

        IdentityKeyStore.loadOrCreate(file);

        assertTrue(Files.exists(file));
    }

    @Test
    void secondCallReturnsTheSameIdentity() {
        Path file = tempDir.resolve("identity.properties");

        KeyPair firstRun = IdentityKeyStore.loadOrCreate(file);
        KeyPair secondRun = IdentityKeyStore.loadOrCreate(file);

        assertArrayEquals(firstRun.getPublic().getEncoded(), secondRun.getPublic().getEncoded());
        byte[] signedLastTime = IdentitySignatureService.sign(firstRun.getPrivate(), CHALLENGE);
        assertTrue(IdentitySignatureService.verify(secondRun.getPublic(), signedLastTime, CHALLENGE),
                "Chu ky tao o lan mo app truoc phai van xac thuc duoc bang khoa doc lai");
    }

    @Test
    void createsMissingParentDirectories() {
        Path file = tempDir.resolve(".chat-p2p-java").resolve("nested").resolve("identity.properties");

        IdentityKeyStore.loadOrCreate(file);

        assertTrue(Files.exists(file));
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws IOException {
        IdentityKeyStore.loadOrCreate(tempDir.resolve("identity.properties"));

        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(List.of(tempDir.resolve("identity.properties")), files.toList());
        }
    }

    @Test
    void corruptedFileIsRejectedAndNotOverwritten() throws IOException {
        Path file = tempDir.resolve("identity.properties");
        String garbage = "version=1\npublicKey=khong-phai-base64!!!\nprivateKey=abc\n";
        Files.writeString(file, garbage, StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> IdentityKeyStore.loadOrCreate(file));
        assertEquals(garbage, Files.readString(file, StandardCharsets.UTF_8),
                "Khong duoc tu ghi de file hong bang khoa moi - se lam mat danh tinh cu");
    }

    @Test
    void unknownVersionIsRejected() throws IOException {
        Path file = tempDir.resolve("identity.properties");
        IdentityKeyStore.loadOrCreate(file);
        String content = Files.readString(file, StandardCharsets.UTF_8).replace("version=1", "version=99");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> IdentityKeyStore.loadOrCreate(file));
    }

    @Test
    void publicAndPrivateKeyFromDifferentPairsAreRejected() throws IOException {
        Path file = tempDir.resolve("identity.properties");
        KeyPair alice = IdentitySignatureService.generateIdentityKeyPair();
        KeyPair mallory = IdentitySignatureService.generateIdentityKeyPair();
        Files.writeString(file, "version=1\n"
                + "publicKey=" + Base64.getEncoder().encodeToString(alice.getPublic().getEncoded()) + "\n"
                + "privateKey=" + Base64.getEncoder().encodeToString(mallory.getPrivate().getEncoded()) + "\n",
                StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> IdentityKeyStore.loadOrCreate(file));
    }
}
