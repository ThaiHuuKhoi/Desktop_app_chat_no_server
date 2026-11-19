package com.datn.chatp2p.p2p.filetransfer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Tinh SHA-256 cua ca file de kiem tra file nhan ve nguyen ven
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D3).
 */
final class Sha256 {

    /** Doc file tung khoi 64 KB - khong nap ca file vao RAM. */
    private static final int BUFFER_SIZE = 64 * 1024;

    private Sha256() {
        // chi chua ham static
    }

    /**
     * @return SHA-256 cua file dang 64 ky tu hex chu thuong (D3).
     */
    static String ofFile(Path file) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                // Chi bam n byte vua doc - lan doc cuoi thuong khong day buffer
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Kiem tra truong sha256 trong FILE_OFFER (du lieu tu peer, khong tin cay):
     * phai dung 64 ky tu [0-9a-f].
     */
    static boolean isValidHex(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Moi JDK deu bat buoc ho tro SHA-256 - khong bao gio xay ra
            throw new IllegalStateException("JDK thieu thuat toan SHA-256", e);
        }
    }
}
