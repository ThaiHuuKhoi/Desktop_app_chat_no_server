package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.RoomConfig;

/**
 * Quy tac o sanh cho (man hinh Home) khi tao / vao phong - tach rieng khoi JavaFX de test duoc.
 *
 * <p>Luu y thiet ke: KHONG co buoc "tao phong" that tren server - server tu tao phong khi co nguoi
 * dau tien JOIN. "Tao" va "vao" cung 1 luong; hop thoai xac nhan cho nguoi dung biet minh se vao
 * phong cong khai hay co mat khau, va phong cong khai do da co bao nhieu nguoi (neu biet).
 */
final class LobbyRules {

    /** Mat khau phong ngan qua de bi do tu "the" phong tren server (du da qua PBKDF2). */
    static final int MIN_ROOM_PASSWORD_LENGTH = 6;

    private LobbyRules() {
    }

    /**
     * Bo khoang trang 2 dau: go thua dau cach la loi rat hay gap va se lam 2 nguoi "cung mat khau"
     * roi vao 2 phong khac nhau ma khong ai biet. Ca 2 phia deu bo nen van nhat quan.
     */
    static String normalizePassword(String raw) {
        return raw == null ? "" : raw.strip();
    }

    /** @return thong bao loi cho nguoi dung, hoac null neu hop le. */
    static String validate(String userName, String roomName, String password) {
        if (userName.isEmpty() || roomName.isEmpty()) {
            return "Vui lòng nhập tên hiển thị và tên phòng.";
        }
        if (roomName.indexOf(RoomConfig.PRIVATE_ROOM_SEPARATOR) >= 0) {
            return "Tên phòng không được chứa ký tự \"" + RoomConfig.PRIVATE_ROOM_SEPARATOR + "\".";
        }
        if (!password.isEmpty() && password.length() < MIN_ROOM_PASSWORD_LENGTH) {
            return "Mật khẩu phòng cần ít nhất " + MIN_ROOM_PASSWORD_LENGTH
                    + " ký tự (hoặc để trống nếu là phòng công khai).";
        }
        return null;
    }

    /**
     * Noi dung hop thoai xac nhan - dung chung cho moi nut vao phong.
     *
     * @param peersInPublicRoom so nguoi dang o phong cong khai cung ten (null = khong biet, vd che do khach).
     */
    static String confirmText(String roomName, String password, Integer peersInPublicRoom) {
        if (!password.isEmpty()) {
            return "Phòng \"" + roomName + "\" có mật khẩu.\n"
                    + "Chỉ người nhập đúng tên phòng và mật khẩu (phân biệt hoa thường) mới thấy bạn. "
                    + "Nếu sai mật khẩu, bạn sẽ ở một phòng trống khác.";
        }
        String base = "Phòng \"" + roomName + "\" là phòng công khai: ai biết tên phòng đều vào được.";
        if (peersInPublicRoom == null) {
            return base;
        }
        return peersInPublicRoom > 0
                ? base + "\nPhòng này đang có " + peersInPublicRoom + " người — bạn sẽ vào chung phòng với họ."
                : base + "\nPhòng chưa có ai — bạn sẽ là người đầu tiên.";
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
