package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyRulesTest {

    @Test
    void passwordIsTrimmedSoAccidentalSpacesDoNotSplitRooms() {
        assertEquals("matkhau1", LobbyRules.normalizePassword("  matkhau1 "));
        assertEquals("", LobbyRules.normalizePassword("   "), "Chi co khoang trang = phong cong khai");
        assertEquals("", LobbyRules.normalizePassword(null));
        assertEquals("Mat Khau", LobbyRules.normalizePassword("Mat Khau"), "Khoang trang o giua van giu");
    }

    @Test
    void validatesNamesAndPasswordLength() {
        assertNull(LobbyRules.validate("Khoi", "phong-a", ""), "Phong cong khai hop le");
        assertNull(LobbyRules.validate("Khoi", "phong-a", "123456"));
        assertNotNull(LobbyRules.validate("", "phong-a", ""));
        assertNotNull(LobbyRules.validate("Khoi", "", ""));
        assertNotNull(LobbyRules.validate("Khoi", "phong~a", ""), "Khong cho ky tu ngan cach ~");
        assertTrue(LobbyRules.validate("Khoi", "phong-a", "12345").contains("ít nhất 6"));
    }

    @Test
    void confirmTextExplainsRoomTypeAndOccupancy() {
        assertTrue(LobbyRules.confirmText("a", "secret1", null).contains("có mật khẩu"));
        assertTrue(LobbyRules.confirmText("a", "", null).contains("công khai"));
        assertTrue(LobbyRules.confirmText("a", "", 3).contains("đang có 3 người"));
        assertTrue(LobbyRules.confirmText("a", "", 0).contains("người đầu tiên"));
        assertTrue(LobbyRules.confirmText("a", "secret1", 3).contains("có mật khẩu"),
                "So nguoi cua phong CONG KHAI cung ten khong lien quan toi phong co mat khau");
    }

    @Test
    void aloneHintMentionsPasswordOnlyForPasswordRooms() {
        assertTrue(LobbyRules.aloneHint(true).contains("mật khẩu"));
        assertTrue(LobbyRules.aloneHint(false).contains("không đặt mật khẩu"));
    }
}
