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
    void savedRoomsAreListedPerAccountNewestFirstWithoutLeakingNameOrPassword() throws Exception {
        LocalHistoryStore store = new LocalHistoryStore(dir, REVERSE);
        List<LocalHistoryStore.SavedMessage> one = List.of(
                new LocalHistoryStore.SavedMessage("m1", true, "khoi", "xin chao", 1, null));
        store.save("nhom-do-an", "nhom-do-an", false, "khoi", one);
        Thread.sleep(5);
        store.save("phong-rieng~aaaaaaaaaaaaaaaaaaaaaaaa", "phong-rieng", true, "khoi",
                List.of(one.get(0), new LocalHistoryStore.SavedMessage("m2", false, "Huy", "ok", 2, null)));
        store.save("phong-cua-huy", "phong-cua-huy", false, "huy", one);
        store.save("phong-khach", "phong-khach", false, "", one);
        store.save("phong-khong-ten", one); // luu kieu cu, khong co ten phong

        List<LocalHistoryStore.SavedRoom> rooms = store.listRooms("khoi");

        assertEquals(List.of("phong-rieng", "nhom-do-an"), rooms.stream().map(LocalHistoryStore.SavedRoom::roomName).toList(),
                "Chi phong cua tai khoan khoi, moi luu nhat truoc");
        assertTrue(rooms.get(0).passwordProtected());
        assertEquals(2, rooms.get(0).messageCount());
        assertEquals("phong-rieng~aaaaaaaaaaaaaaaaaaaaaaaa", rooms.get(0).roomKey());
        assertTrue(store.listRooms("").isEmpty(), "Khach khong co muc phong da luu");
        assertEquals(one, store.load("nhom-do-an"), "Tin van nap lai binh thuong");

        Path file = store.fileFor("phong-rieng~aaaaaaaaaaaaaaaaaaaaaaaa");
        assertFalse(file.getFileName().toString().contains("phong"), "Ten file van khong lo ten phong");
        assertFalse(new String(Files.readAllBytes(file)).contains("phong-rieng"), "Ten phong nam trong phan ma hoa");
    }

    @Test
    void oldFormatFilesStillLoadAndSingleRoomCanBeDeleted() throws Exception {
        LocalHistoryStore store = new LocalHistoryStore(dir, REVERSE);
        List<LocalHistoryStore.SavedMessage> messages = List.of(
                new LocalHistoryStore.SavedMessage("m1", true, "Toi", "tin cu", 1, null));
        // File dinh dang cu: chi la danh sach tin (truoc 25/11).
        Files.createDirectories(dir);
        Files.write(store.fileFor("phong-cu"), REVERSE.protect(
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(messages)));

        assertEquals(messages, store.load("phong-cu"));
        assertTrue(store.listRooms("khoi").isEmpty(), "File cu khong co ten phong nen khong hien trong danh sach");

        store.save("a", "a", false, "khoi", messages);
        assertTrue(store.delete("a"));
        assertTrue(store.load("a").isEmpty());
        assertFalse(store.delete("a"));
        assertEquals(messages, store.load("phong-cu"), "Xoa 1 phong khong anh huong phong khac");
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
