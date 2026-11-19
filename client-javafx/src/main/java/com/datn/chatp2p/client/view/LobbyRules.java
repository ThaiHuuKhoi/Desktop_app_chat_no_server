package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import com.datn.chatp2p.client.session.RoomConfig;

import java.util.List;

/**
 * Quy tac o sanh cho (man hinh Home) - tach rieng khoi JavaFX de test duoc.
 *
 * <p>Luong da thong nhat:
 * <ul>
 *   <li><b>Vao phong</b>: chon 1 phong trong danh sach -&gt; hop xac nhan -&gt; neu phong co mat khau
 *       thi hop nhap mat khau -&gt; vao.</li>
 *   <li><b>Tao phong</b>: bam "Tao phong" -&gt; hop "Thong tin phong" (ten, tuy chon dat mat khau) -&gt; vao.</li>
 *   <li><b>Vao phong bang ten</b> (khach khong co danh sach, hoac phong chua hien): hop nhap ten + mat
 *       khau neu co.</li>
 * </ul>
 * Server khong co buoc "tao phong" that - phong tu sinh khi co nguoi dau tien JOIN.
 */
final class LobbyRules {

    /** Mat khau phong ngan qua de bi do (du da qua PBKDF2). */
    static final int MIN_ROOM_PASSWORD_LENGTH = 6;

    private LobbyRules() {
    }

    /**
     * Bo khoang trang 2 dau: go thua dau cach la loi rat hay gap va se lam 2 nguoi "cung mat khau"
     * roi vao 2 phong khac nhau ma khong ai biet. Moi phia deu bo nen van nhat quan.
     */
    static String normalizePassword(String raw) {
        return raw == null ? "" : raw.strip();
    }

    static String normalizeName(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /** @return loi cho ten hien thi, hoac null. */
    static String validateUserName(String userName) {
        return userName.isEmpty() ? "Vui lòng nhập tên hiển thị." : null;
    }

    /** @return loi cho ten phong, hoac null. */
    static String validateRoomName(String roomName) {
        if (roomName.isEmpty()) {
            return "Vui lòng nhập tên phòng.";
        }
        if (roomName.indexOf(RoomConfig.PRIVATE_ROOM_SEPARATOR) >= 0) {
            return "Tên phòng không được chứa ký tự \"" + RoomConfig.PRIVATE_ROOM_SEPARATOR + "\".";
        }
        return null;
    }

    /** Kiem tra hop "Thong tin phong" khi tao phong. @return loi, hoac null neu hop le. */
    static String validateCreate(String roomName, boolean usePassword, String password, String confirmPassword) {
        String nameError = validateRoomName(roomName);
        if (nameError != null) {
            return nameError;
        }
        if (!usePassword) {
            return null;
        }
        if (password.length() < MIN_ROOM_PASSWORD_LENGTH) {
            return "Mật khẩu phòng cần ít nhất " + MIN_ROOM_PASSWORD_LENGTH + " ký tự.";
        }
        if (!password.equals(confirmPassword)) {
            return "Mật khẩu nhập lại không khớp.";
        }
        return null;
    }

    /** Kiem tra mat khau nhap khi vao phong co mat khau. */
    static String validateJoinPassword(String password) {
        return password.isEmpty() ? "Vui lòng nhập mật khẩu phòng." : null;
    }

    /** Noi dung hop xac nhan khi chon 1 phong trong danh sach. */
    static String confirmJoinText(RoomSummary room) {
        String people = room.peerCount() > 0 ? "Đang có " + room.peerCount() + " người trong phòng." : "";
        return room.passwordProtected()
                ? "Phòng \"" + room.roomId() + "\" có mật khẩu. " + people
                        + "\nBạn sẽ được hỏi mật khẩu ở bước tiếp theo."
                : "Phòng \"" + room.roomId() + "\" là phòng công khai. " + people;
    }

    /**
     * Thong tin / canh bao trong hop tao phong, tuy ten va lua chon mat khau, so voi danh sach phong dang co.
     *
     * @param activeRooms danh sach phong da tai (rong neu khong biet - vd che do khach).
     */
    static String createInfoText(String roomName, boolean usePassword, List<RoomSummary> activeRooms) {
        String base = usePassword
                ? "Phòng có mật khẩu: chỉ người nhập đúng tên phòng và mật khẩu (phân biệt hoa thường) mới vào được. "
                        + "Mật khẩu không được gửi lên server."
                : "Phòng công khai: ai biết tên phòng đều vào được.";
        if (roomName.isEmpty()) {
            return base;
        }
        boolean publicExists = activeRooms.stream()
                .anyMatch(r -> r.roomId().equals(roomName) && !r.passwordProtected());
        boolean privateExists = activeRooms.stream()
                .anyMatch(r -> r.roomId().equals(roomName) && r.passwordProtected());
        if (!usePassword && publicExists) {
            return base + "\n⚠ Đã có phòng công khai tên này — bạn sẽ vào chung phòng với những người đang ở đó.";
        }
        if (usePassword && privateExists) {
            return base + "\n⚠ Đã có phòng có mật khẩu tên này. Nếu mật khẩu của bạn khác, bạn sẽ ở một phòng riêng.";
        }
        return base;
    }

    /** Goi y khi da o mot minh trong phong mot luc (khong phan biet duoc "sai mat khau" voi "chua ai vao"). */
    static String aloneHint(boolean passwordProtected) {
        return passwordProtected
                ? "Vẫn chưa có ai trong phòng. Nếu bạn bè đã vào, hãy kiểm tra lại tên phòng và mật khẩu "
                        + "(phân biệt hoa thường) — người nhập khác mật khẩu sẽ ở một phòng khác."
                : "Vẫn chưa có ai trong phòng. Nếu bạn bè đã vào, hãy kiểm tra lại tên phòng (phân biệt hoa thường) "
                        + "và chắc chắn họ không đặt mật khẩu.";
    }
}
