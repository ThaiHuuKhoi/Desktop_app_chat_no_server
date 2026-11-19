package com.datn.chatp2p.client.util;

import com.datn.chatp2p.crypto.KeyProtector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHistoryStoreTest {

    /** Bo "ma hoa" gia: dao nguoc byte - du de thay file khong con chua noi dung ro. */
    private static final KeyProtector REVERSE = new KeyProtector() {
        @Override
        public String name() {
            return "reverse-test";
        }

        @Override
        public byte[] protect(byte[] secret) {
            byte[] out = new byte[secret.length];
            for (int i = 0; i < secret.length; i++) {
                out[i] = (byte) ~secret[secret.length - 1 - i];
            }
            return out;
        }

        @Override
        public byte[] unprotect(byte[] protectedSecret) {
            return protect(protectedSecret);
        }
    };

    @TempDir
    Path dir;

    @Test
    void savesEncryptedAndLoadsBackPerRoom() throws Exception {
        LocalHistoryStore store = new LocalHistoryStore(dir, REVERSE);
        List<LocalHistoryStore.SavedMessage> messages = List.of(
                new LocalHistoryStore.SavedMessage("m1", true, "Toi", "bi mat cua phong", 1, null),
                new LocalHistoryStore.SavedMessage("m2", false, "An", "tra loi", 2, "m1"));

        store.save("phong~the1", messages);

        assertEquals(messages, store.load("phong~the1"));
        assertTrue(store.load("phong~the2").isEmpty(), "Cung ten khac mat khau la phong khac");
        Path file = store.fileFor("phong~the1");
        assertFalse(file.getFileName().toString().contains("phong"), "Ten file khong lo ten phong");
        assertFalse(new String(Files.readAllBytes(file)).contains("bi mat"), "Noi dung da duoc ma hoa");
    }

    @Test
    void keepsOnlyTheMostRecentMessages() {
        LocalHistoryStore store = new LocalHistoryStore(dir, REVERSE);
        List<LocalHistoryStore.SavedMessage> many = new ArrayList<>();
        for (int i = 0; i < LocalHistoryStore.MAX_MESSAGES + 20; i++) {
            many.add(new LocalHistoryStore.SavedMessage("m" + i, false, "An", "tin " + i, i, null));
        }
        store.save("p", many);

        List<LocalHistoryStore.SavedMessage> loaded = store.load("p");
        assertEquals(LocalHistoryStore.MAX_MESSAGES, loaded.size());
        assertEquals("m20", loaded.get(0).id());
    }

    @Test
    void withoutEncryptionNothingIsSavedAndDeleteAllWorks() {
        LocalHistoryStore plain = new LocalHistoryStore(dir, null);
        plain.save("p", List.of(new LocalHistoryStore.SavedMessage("m", true, "Toi", "x", 1, null)));
        assertFalse(plain.available());
        assertTrue(plain.load("p").isEmpty(), "Khong co bo ma hoa thi khong luu dang thuong");

        LocalHistoryStore store = new LocalHistoryStore(dir, REVERSE);
        store.save("a", List.of(new LocalHistoryStore.SavedMessage("m", true, "Toi", "x", 1, null)));
        store.save("b", List.of(new LocalHistoryStore.SavedMessage("m", true, "Toi", "y", 1, null)));
        assertEquals(2, store.deleteAll());
        assertTrue(store.load("a").isEmpty());
    }
}
