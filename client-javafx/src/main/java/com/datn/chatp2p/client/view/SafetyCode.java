package com.datn.chatp2p.client.view;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Locale;
import java.util.Map;

/**
 * "Ma an toan" chung cua 2 nguoi (giong safety number cua Signal): ghep 2 van tay theo thu tu co
 * dinh (sap xep), nen CA HAI may sinh ra CUNG 1 chuoi va cung 1 ma QR. Hai nguoi chi can so 2 ma QR
 * (de canh nhau, hoac quet bang dien thoai) thay vi doc 2 day 64 ky tu cho nhau.
 */
final class SafetyCode {

    private SafetyCode() {
    }

    /** Chuoi chung cho 2 van tay - doi cho 2 tham so van ra cung ket qua. */
    static String combine(String fingerprintA, String fingerprintB) {
        String a = normalize(fingerprintA);
        String b = normalize(fingerprintB);
        return a.compareTo(b) <= 0 ? "chatp2p-safety:" + a + ":" + b : "chatp2p-safety:" + b + ":" + a;
    }

    /** So nhom 5 chu so trong ma an toan dang so. */
    static final int NUMBER_GROUPS = 12;

    /**
     * Ma an toan dang SO de doc to cho nhau: 12 nhom x 5 chu so (kieu "safety number" cua Signal).
     * SHA-512 cua {@code combined} (ket qua {@link #combine}, giong het tren 2 may) chia thanh 12 khoi
     * 5 byte, moi khoi lay phan du cho 100000 - tong cong ~199 bit, de doc hon 2 day hex 64 ky tu.
     */
    static java.util.List<String> numberGroups(String combined) {
        byte[] hash;
        try {
            hash = java.security.MessageDigest.getInstance("SHA-512")
                    .digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 khong kha dung", e);
        }
        java.util.List<String> groups = new java.util.ArrayList<>(NUMBER_GROUPS);
        for (int i = 0; i < NUMBER_GROUPS; i++) {
            long chunk = 0;
            for (int b = 0; b < 5; b++) {
                chunk = (chunk << 8) | (hash[i * 5 + b] & 0xFF);
            }
            groups.add(String.format(Locale.ROOT, "%05d", chunk % 100_000));
        }
        return groups;
    }

    /** Ma tran QR (true = o den) cua chuoi {@code text}. */
    static boolean[][] qrMatrix(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            boolean[][] cells = new boolean[matrix.getHeight()][matrix.getWidth()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    cells[y][x] = matrix.get(x, y);
                }
            }
            return cells;
        } catch (WriterException e) {
            throw new IllegalStateException("Khong tao duoc ma QR", e);
        }
    }

    private static String normalize(String fingerprint) {
        return fingerprint == null ? "" : fingerprint.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
