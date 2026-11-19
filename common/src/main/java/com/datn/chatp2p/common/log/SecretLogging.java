package com.datn.chatp2p.common.log;

import java.util.HexFormat;

/**
 * Che do DEBUG ghi ca du lieu nhay cam vao log: noi dung tin nhan, mat khau (tai khoan + phong),
 * token phien, khoa phien AES. CHI dung khi test tren may minh.
 *
 * <p>Mac dinh TAT. Bat bang {@code -Dchat.logSecrets=true} hoac bien moi truong
 * {@code CHAT_P2P_LOG_SECRETS=true}. Khi tat, {@link #reveal} tra ve chuoi da che.
 *
 * <p>Khong bao gio log private key danh tinh ECDSA lau dai (ai co khoa do mao danh duoc vinh vien;
 * neu can xem thi no nam san trong {@code ~/.chat-p2p-java/identity.properties}).
 */
public final class SecretLogging {

    private static final boolean ENABLED = Boolean.getBoolean("chat.logSecrets")
            || "true".equalsIgnoreCase(System.getenv("CHAT_P2P_LOG_SECRETS"));

    /** Canh bao in o dau log khi che do nay bat. */
    public static final String WARNING_BANNER =
            "!!! CHE DO LOG BI MAT DANG BAT (chat.logSecrets=true): log se chua NOI DUNG TIN NHAN, MAT KHAU, "
                    + "TOKEN va KHOA PHIEN. Chi dung khi test, KHONG chia se log nay, KHONG bat tren may that. !!!";

    private SecretLogging() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Gia tri that neu che do bat, nguoc lai "***" (hoac "(rong)" neu khong co gia tri). */
    public static String reveal(String value) {
        if (value == null || value.isEmpty()) {
            return "(rong)";
        }
        return ENABLED ? value : "***";
    }

    /** Nhu {@link #reveal(String)} cho du lieu nhi phan (vd khoa) - in dang hex. */
    public static String revealHex(byte[] value) {
        if (value == null || value.length == 0) {
            return "(rong)";
        }
        return ENABLED ? HexFormat.of().formatHex(value) : "***";
    }

    /** Noi dung tin nhan cho log: ca doan van ban khi bat (xuong dong hien la \n), khong thi chi do dai. */
    public static String text(String value) {
        if (value == null) {
            return "(null)";
        }
        return ENABLED
                ? "\"" + value.replace("\r", "").replace("\n", "\\n") + "\""
                : value.length() + " ky tu";
    }
}
