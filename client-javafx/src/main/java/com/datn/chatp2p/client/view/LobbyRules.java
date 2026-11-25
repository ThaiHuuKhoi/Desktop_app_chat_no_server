package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.util.LocalHistoryStore;
import com.datn.chatp2p.common.signal.JoinRejectReason;

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

    /** Khop {@code RoomRegistry.MAX_PEERS_PER_ROOM} cua signaling server (mesh toi da 8 nguoi). */
    static final int MAX_PEERS_PER_ROOM = 8;

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

    /** Phong da du nguoi (server se tu choi ROOM_FULL). */
    static boolean isFull(RoomSummary room) {
        return room.peerCount() >= MAX_PEERS_PER_ROOM;
    }

    /** Dong mo ta duoi ten phong khi xem truoc, vd "3/8 người · tạo bởi Lan · có mật khẩu". */
    static String previewMeta(RoomSummary room) {
        StringBuilder text = new StringBuilder(room.peerCount() + "/" + MAX_PEERS_PER_ROOM + " người");
        if (room.ownerUserName() != null && !room.ownerUserName().isBlank()) {
            text.append(" · tạo bởi ").append(room.ownerUserName());
        }
        text.append(room.passwordProtected() ? " · có mật khẩu" : " · công khai");
        return text.toString();
    }

    private static final java.time.format.DateTimeFormatter SAVED_AT_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    /** Phong dang hoat dong trung voi phong da luu (cung ten, cung loai co / khong mat khau), hoac null. */
    static RoomSummary activeMatch(LocalHistoryStore.SavedRoom saved, List<RoomSummary> activeRooms) {
        return activeRooms.stream()
                .filter(room -> room.roomId().equals(saved.roomName())
                        && room.passwordProtected() == saved.passwordProtected())
                .findFirst().orElse(null);
    }

    /** Dong mo ta duoi ten phong khi xem truoc 1 phong da luu. {@code active} null = phong hien khong co ai. */
    static String savedRoomMeta(LocalHistoryStore.SavedRoom saved, RoomSummary active, java.time.ZoneId zone) {
        String when = SAVED_AT_FORMAT.format(java.time.Instant.ofEpochMilli(saved.savedAt()).atZone(zone));
        return "Đã lưu " + saved.messageCount() + " tin · lần cuối " + when
                + (saved.passwordProtected() ? " · có mật khẩu" : " · công khai")
                + (active == null
                        ? " · hiện không có ai trong phòng"
                        : " · đang có " + active.peerCount() + "/" + MAX_PEERS_PER_ROOM + " người");
    }

    /** Goi y duoi nut "Vao phong" khi xem truoc 1 phong da luu. */
    static String savedRoomHint(boolean passwordProtected, boolean roomActive, boolean saveHistoryEnabled) {
        StringBuilder hint = new StringBuilder(passwordProtected
                ? "Nhập đúng mật khẩu đã dùng lần trước để vào lại và hiện lại tin đã lưu. Mật khẩu không được lưu trên máy."
                : "Bấm Vào phòng để vào lại; tin đã lưu sẽ hiện lại.");
        if (!roomActive) {
            hint.append(" Phòng hiện không có ai: vào phòng sẽ mở lại phòng, bạn bè vào cùng tên")
                    .append(passwordProtected ? " và mật khẩu" : "").append(" là gặp lại nhau.");
        }
        if (!saveHistoryEnabled) {
            hint.append(" Lưu ý: tuỳ chọn lưu lịch sử đang tắt trong Cài đặt, nên vào phòng sẽ không hiện lại tin đã lưu.");
        }
        return hint.toString();
    }

    /** Goi y duoi nut "Vao phong" khi xem truoc. {@code room == null}: phong nhap theo ten. */
    static String previewHint(RoomSummary room) {
        if (room == null) {
            return "Phòng không có trong danh sách (hoặc bạn đang là khách). Nếu phòng có mật khẩu, nhập đúng "
                    + "mật khẩu bạn bè đã gửi; để trống nếu là phòng công khai. Tên phòng phân biệt hoa thường.";
        }
        if (isFull(room)) {
            return "Phòng đã đủ " + MAX_PEERS_PER_ROOM + " người, hãy thử lại sau.";
        }
        return room.passwordProtected()
                ? "Sai mật khẩu sẽ được báo ngay. Mật khẩu chỉ dùng trên máy bạn để tạo khoá phòng."
                : "Phòng công khai: ai biết tên phòng đều vào được.";
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
            return base + "\n⚠ Đã có phòng có mật khẩu tên này. Nếu mật khẩu của bạn khác, bạn sẽ bị báo sai mật khẩu — hãy chọn tên khác.";
        }
        return base;
    }

    /** Goi y khi da o mot minh trong phong mot luc (khong phan biet duoc "sai mat khau" voi "chua ai vao"). */
    static String aloneHint(boolean passwordProtected) {
        // Sai mat khau gio duoc server bao ngay (JOIN_REJECTED), nen goi y nay chi con cho truong hop
        // sai TEN phong hoac ban be chua vao.
        return passwordProtected
                ? "Vẫn chưa có ai trong phòng. Nếu bạn bè đã vào, hãy kiểm tra lại tên phòng (phân biệt hoa thường). "
                        + "Nếu nhập sai mật khẩu, bạn sẽ được báo ngay."
                : "Vẫn chưa có ai trong phòng. Nếu bạn bè đã vào, hãy kiểm tra lại tên phòng (phân biệt hoa thường) "
                        + "và chắc chắn họ không đặt mật khẩu.";
    }

    /** Thong bao cho nguoi dung khi server tu choi cho vao phong ({@link JoinRejectReason}). */
    static String joinRejectedMessage(String reason, String roomName) {
        String room = "\"" + roomName + "\"";
        if (reason == null) {
            return "Không vào được phòng " + room + ".";
        }
        return switch (reason) {
            case JoinRejectReason.WRONG_PASSWORD -> "Sai mật khẩu phòng " + room + ". Vui lòng nhập lại "
                    + "(phân biệt hoa thường). Nếu bạn đang tạo phòng mới, hãy chọn tên khác vì tên này đã có người dùng.";
            case JoinRejectReason.ROOM_FULL -> "Phòng " + room + " đã đủ người (tối đa 8 người).";
            case JoinRejectReason.AUTH_REQUIRED -> "Server yêu cầu đăng nhập trước khi vào phòng.";
            case JoinRejectReason.AUTH_EXPIRED -> "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.";
            case JoinRejectReason.RATE_LIMITED -> "Bạn đã nhập sai mật khẩu quá nhiều lần. Vui lòng thử lại sau 1 phút.";
            case JoinRejectReason.DUPLICATE_PEER -> "Không vào được phòng " + room + " (trùng mã phiên). Vui lòng thử lại.";
            case JoinRejectReason.INVALID_JOIN -> "Tên phòng hoặc tên hiển thị không hợp lệ (có thể quá dài).";
            case JoinRejectReason.SERVER_BUSY -> "Server đang quá tải, vui lòng thử lại sau ít phút.";
            default -> "Server từ chối cho vào phòng " + room + " (" + reason + ").";
        };
    }

    /** Ly do tu choi can dang nhap lai (token thieu / het han). */
    static boolean requiresLogin(String reason) {
        return JoinRejectReason.AUTH_REQUIRED.equals(reason) || JoinRejectReason.AUTH_EXPIRED.equals(reason);
    }
}
