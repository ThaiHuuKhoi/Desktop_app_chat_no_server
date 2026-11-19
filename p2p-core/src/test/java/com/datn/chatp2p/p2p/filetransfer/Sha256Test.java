package com.datn.chatp2p.p2p.filetransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sha256Test {

    @TempDir
    Path dir;   // JUnit tu tao thu muc tam va xoa sau khi test xong

    @Test
    void emptyFileHasTheWellKnownHash() throws Exception {
        Path file = Files.createFile(dir.resolve("rong.bin"));

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Sha256.ofFile(file));
    }

    @Test
    void knownTextHash() throws Exception {
        Path file = Files.writeString(dir.resolve("abc.txt"), "abc");

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256.ofFile(file));
    }

    @Test
    void largeFileMatchesHashingAllBytesAtOnce() throws Exception {
        // 200 000 byte = 3 khoi 64 KB + 1 khoi le -> bat loi update(buffer) thieu do dai
        byte[] data = new byte[200_000];
        new Random(1).nextBytes(data);
        Path file = Files.write(dir.resolve("lon.bin"), data);

        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(data));

        assertEquals(expected, Sha256.ofFile(file));
    }

    @Test
    void isValidHexAcceptsOnly64LowercaseHexChars() {
        assertTrue(Sha256.isValidHex("a".repeat(64)));
        assertTrue(Sha256.isValidHex("0123456789abcdef".repeat(4)));

        assertFalse(Sha256.isValidHex(null));
        assertFalse(Sha256.isValidHex(""));
        assertFalse(Sha256.isValidHex("a".repeat(63)));   // thieu 1 ky tu
        assertFalse(Sha256.isValidHex("a".repeat(65)));   // thua 1 ky tu
        assertFalse(Sha256.isValidHex("A".repeat(64)));   // chu hoa
        assertFalse(Sha256.isValidHex("g".repeat(64)));   // khong phai hex
    }
}
