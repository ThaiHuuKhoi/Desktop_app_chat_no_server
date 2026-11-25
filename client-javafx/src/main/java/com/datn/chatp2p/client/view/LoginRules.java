package com.datn.chatp2p.client.view;

/** Quy tac / cau chu cua man hinh dang nhap - khong dung JavaFX de test duoc. */
final class LoginRules {

    /** Khop {@code AuthService} cua server (mat khau tai khoan toi thieu 8 ky tu). */
    static final int MIN_ACCOUNT_PASSWORD_LENGTH = 8;

    private LoginRules() {
    }

    /**
     * Server tu choi vi khoa danh tinh cua may nay khac khoa da gan voi tai khoan (thuong la dang
     * nhap tren may moi) - thong diep cua {@code AuthService.login}: "Khóa identity không khớp tài khoản này".
     */
    static boolean isKeyMismatch(String serverMessage) {
        return serverMessage != null && serverMessage.toLowerCase(java.util.Locale.ROOT).contains("identity không khớp");
    }

    /** 3 nhom dau cua van tay, vd "3F9C A1D2 7B40 …" (du de nhan ra, khong thay cho so van tay day du). */
    static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "";
        }
        String[] groups = fingerprint.strip().split("\\s+");
        if (groups.length >= 3) {
            return groups[0] + " " + groups[1] + " " + groups[2] + " …";
        }
        String compact = fingerprint.replaceAll("\\s+", "");
        return compact.length() <= 12 ? compact : compact.substring(0, 12) + " …";
    }

    /** Kiem tra form dang ky phia client. @return loi, hoac null neu hop le. */
    static String validateRegistration(String username, String password, String confirm) {
        if (username.isEmpty()) {
            return "Vui lòng nhập tên tài khoản.";
        }
        if (password.length() < MIN_ACCOUNT_PASSWORD_LENGTH) {
            return "Mật khẩu cần ít nhất " + MIN_ACCOUNT_PASSWORD_LENGTH + " ký tự.";
        }
        if (!password.equals(confirm)) {
            return "Mật khẩu nhập lại không khớp.";
        }
        return null;
    }

    /** Kiem tra form doi mat khau phia client (server van kiem tra lai). @return loi, hoac null neu hop le. */
    static String validatePasswordChange(String current, String next, String confirm) {
        if (current.isEmpty()) {
            return "Vui lòng nhập mật khẩu hiện tại.";
        }
        if (next.length() < MIN_ACCOUNT_PASSWORD_LENGTH) {
            return "Mật khẩu mới cần ít nhất " + MIN_ACCOUNT_PASSWORD_LENGTH + " ký tự.";
        }
        if (next.equals(current)) {
            return "Mật khẩu mới phải khác mật khẩu hiện tại.";
        }
        if (!next.equals(confirm)) {
            return "Mật khẩu nhập lại không khớp.";
        }
        return null;
    }
}
