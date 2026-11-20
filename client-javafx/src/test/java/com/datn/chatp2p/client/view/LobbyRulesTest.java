package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyRulesTest {

    @Test
    void passwordIsTrimmedSoAccidentalSpacesDoNotSplitRooms() {
        assertEquals("matkhau1", LobbyRules.normalizePassword("  matkhau1 "));
        assertEquals("", LobbyRules.normalizePassword("   "));
        assertEquals("", LobbyRules.normalizePassword(null));
        assertEquals("Mat Khau", LobbyRules.normalizePassword("Mat Khau"), "Khoang trang o giua van giu");
    }

    @Test
    void validatesCreateDialog() {
        assertNull(LobbyRules.validateCreate("phong-a", false, "", ""), "Phong cong khai hop le");
        assertNull(LobbyRules.validateCreate("phong-a", true, "123456", "123456"));
        assertNotNull(LobbyRules.validateCreate("", false, "", ""));
        assertNotNull(LobbyRules.validateCreate("phong~a", false, "", ""), "Khong cho ky tu ngan cach ~");
        assertTrue(LobbyRules.validateCreate("phong-a", true, "12345", "12345").contains("ít nhất 6"));
        assertTrue(LobbyRules.validateCreate("phong-a", true, "123456", "123457").contains("không khớp"));
        assertNull(LobbyRules.validateCreate("phong-a", false, "abc", "xyz"),
                "Khong dat mat khau thi bo qua o mat khau");
    }

    @Test
    void validatesUserNameAndJoinPassword() {
        assertNotNull(LobbyRules.validateUserName(""));
        assertNull(LobbyRules.validateUserName("Khoi"));
        assertNotNull(LobbyRules.validateJoinPassword(""));
        assertNull(LobbyRules.validateJoinPassword("bat-ky"));
    }

    @Test
    void createInfoWarnsAboutExistingRoomsWithSameName() {
        List<RoomSummary> rooms = List.of(
                new RoomSummary("chung", 2, "An", false),
                new RoomSummary("rieng", 1, "Binh", true));
        assertTrue(LobbyRules.createInfoText("chung", false, rooms).contains("vào chung phòng"));
        assertTrue(LobbyRules.createInfoText("rieng", true, rooms).contains("chọn tên khác"));
        assertFalse(LobbyRules.createInfoText("moi", false, rooms).contains("⚠"));
        assertFalse(LobbyRules.createInfoText("chung", true, rooms).contains("⚠"),
                "Phong co mat khau khong trung voi phong cong khai cung ten");
    }

    @Test
    void oldServerRoomIdsWithTagAreNormalized() {
        RoomSummary fromOldServer = new RoomSummary("rieng~3f9c0a1b2c3d4e5f60718293", 2, "An", false).normalized();
        assertEquals(new RoomSummary("rieng", 2, "An", true), fromOldServer);
        RoomSummary publicRoom = new RoomSummary("chung", 1, "An", false);
        assertEquals(publicRoom, publicRoom.normalized());
    }

    @Test
    void aloneHintMentionsPasswordOnlyForPasswordRooms() {
        assertTrue(LobbyRules.aloneHint(true).contains("mật khẩu"));
        assertTrue(LobbyRules.aloneHint(false).contains("không đặt mật khẩu"));
    }

    @Test
    void joinRejectedMessagesNameTheProblem() {
        assertTrue(LobbyRules.joinRejectedMessage("WRONG_PASSWORD", "ban-be").contains("Sai mật khẩu phòng \"ban-be\""));
        assertTrue(LobbyRules.joinRejectedMessage("ROOM_FULL", "ban-be").contains("đủ người"));
        assertTrue(LobbyRules.joinRejectedMessage("RATE_LIMITED", "ban-be").contains("1 phút"));
        assertTrue(LobbyRules.joinRejectedMessage("AUTH_EXPIRED", "ban-be").contains("đăng nhập lại"));
        assertTrue(LobbyRules.joinRejectedMessage("SOMETHING_NEW", "ban-be").contains("SOMETHING_NEW"));
        assertTrue(LobbyRules.requiresLogin("AUTH_EXPIRED"));
        assertTrue(LobbyRules.requiresLogin("AUTH_REQUIRED"));
        assertFalse(LobbyRules.requiresLogin("WRONG_PASSWORD"));
    }

    @Test
    void previewDescribesRoomAndFullness() {
        RoomSummary locked = new RoomSummary("phong-do-an", 3, "Lan", true);
        assertEquals("3/8 người · tạo bởi Lan · có mật khẩu", LobbyRules.previewMeta(locked));
        assertEquals("5/8 người · công khai", LobbyRules.previewMeta(new RoomSummary("lop", 5, null, false)));

        assertFalse(LobbyRules.isFull(locked));
        RoomSummary full = new RoomSummary("cafe", 8, "Ha", false);
        assertTrue(LobbyRules.isFull(full));
        assertTrue(LobbyRules.previewHint(full).contains("đủ 8 người"));
        assertTrue(LobbyRules.previewHint(locked).contains("Sai mật khẩu sẽ được báo ngay"));
        assertTrue(LobbyRules.previewHint(null).contains("để trống nếu là phòng công khai"),
                "Phong nhap theo ten: mat khau tuy chon");
    }
}
