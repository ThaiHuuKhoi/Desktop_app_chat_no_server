package com.datn.chatp2p.p2p.filetransfer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lam sach ten file do ben gui tu khai (khong tin cay) truoc khi ghi xuong dia
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D10).
 */
public final class FileNames {

    /** Ten dung khi ten goc rong hoac khong dung duoc. */
    static final String FALLBACK_NAME = "file";

    /** Do dai toi da cua ten file sau khi lam sach (Windows cho toi da 255). */
    static final int MAX_NAME_LENGTH = 200;

    /** Ky tu cam tren Windows va ky tu dieu khien 0..31. */
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");

    /** Ten thiet bi cua Windows - mo file ten "CON.txt" se mo thiet bi, khong phai file. */
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNames() {
        // chi chua ham static
    }

    /**
     * Tra ve ten file an toan: khong chua duong dan, khong co ky tu cam,
     * khong trung ten thiet bi Windows, dai toi da {@value #MAX_NAME_LENGTH} ky tu.
     */
    public static String sanitize(String rawName) {
        if (rawName == null) {
            return FALLBACK_NAME;
        }

        // 1. Chi giu phan sau dau / hoac \ cuoi cung -> chan "../../evil.exe", "C:\Windows\x.dll"
        int lastSeparator = Math.max(rawName.lastIndexOf('/'), rawName.lastIndexOf('\\'));
        String name = rawName.substring(lastSeparator + 1);

        // 2. Thay ky tu cam va ky tu dieu khien bang "_"
        name = FORBIDDEN_CHARS.matcher(name).replaceAll("_");

        // 3. Bo dau cham va khoang trang o cuoi (Windows tu cat chung)
        name = stripTrailingDotsAndSpaces(name);

        // 4. Rong hoac toan dau cham ("." , "..") -> ten mac dinh
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            return FALLBACK_NAME;
        }

        // 5. Ten thiet bi Windows: so phan truoc dau cham dau tien, khong phan biet hoa thuong
        int firstDot = name.indexOf('.');
        String stem = firstDot >= 0 ? name.substring(0, firstDot) : name;
        if (WINDOWS_DEVICE_NAMES.contains(stem.strip().toUpperCase(Locale.ROOT))) {
            name = "_" + name;
        }

        // 6. Qua dai -> cat bot phan ten, giu nguyen duoi file
        return truncate(name);
    }

    /**
     * Tra ve duong dan {@code dir/safeName}; neu da co file trung ten thi thu
     * {@code ten (1).duoi}, {@code ten (2).duoi}... de khong ghi de file co san.
     *
     * @param safeName ten da qua {@link #sanitize(String)}
     */
    public static Path uniqueTarget(Path dir, String safeName) {
        Objects.requireNonNull(dir, "dir");
        Path candidate = childOf(dir, safeName);
        if (!exists(candidate)) {
            return candidate;
        }

        int dot = extensionStart(safeName);
        String base = dot > 0 ? safeName.substring(0, dot) : safeName;
        String extension = dot > 0 ? safeName.substring(dot) : "";
        for (int i = 1; ; i++) {
            candidate = childOf(dir, base + " (" + i + ")" + extension);
            if (!exists(candidate)) {
                return candidate;
            }
        }
    }

    private static Path childOf(Path dir, String name) {
        Path child = dir.resolve(name);
        // Phong thu: ten hop le khong bao gio thoat ra ngoai thu muc da chon
        if (!dir.equals(child.getParent()) || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Ten file khong an toan: " + name);
        }
        return child;
    }

    private static boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static String stripTrailingDotsAndSpaces(String name) {
        int end = name.length();
        while (end > 0 && (name.charAt(end - 1) == '.' || name.charAt(end - 1) == ' ')) {
            end--;
        }
        return name.substring(0, end);
    }

    /** Vi tri dau cham cua duoi file; -1 neu khong co duoi (ten bat dau bang dau cham cung coi la khong co duoi). */
    private static int extensionStart(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? dot : -1;
    }

    private static String truncate(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        int dot = extensionStart(name);
        String extension = dot > 0 ? name.substring(dot) : "";
        if (extension.length() >= MAX_NAME_LENGTH / 2) {
            extension = ""; // "duoi" dai bat thuong -> khong giu
        }
        String base = extension.isEmpty() ? name : name.substring(0, dot);
        int keep = MAX_NAME_LENGTH - extension.length();
        // Khong cat doi 1 ky tu Unicode gom 2 char (vd emoji)
        if (Character.isHighSurrogate(base.charAt(keep - 1))) {
            keep--;
        }
        return base.substring(0, keep) + extension;
    }
}
