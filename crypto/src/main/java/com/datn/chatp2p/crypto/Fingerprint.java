package com.datn.chatp2p.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

/**
 * Sinh "van tay" (fingerprint) ngan, de doc cua mot public key, dung de nguoi
 * dung xac thuc thu cong doi phuong (De-cuong-Chat-P2P-Java.md muc 2, muc 5:
 * "Xac thuc doi phuong qua vân tay khoa cong khai").
 *
 * <p>Hai ben so sanh fingerprint nay qua mot kenh rieng (goi dien, gap truc
 * tiep...) de phat hien tan cong Man-in-the-Middle o buoc trao khoa ECDH.
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    /**
     * Tra ve fingerprint dang hex, viet hoa, nhom 4 ky tu cach nhau boi khoang
     * trang de de doc/so sanh bang mat, vi du: {@code A1B2 C3D4 E5F6 ...}.
     */
    public static String of(PublicKey publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(publicKey.getEncoded());
            return formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 khong kha dung", e);
        }
    }

    private static String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2 + bytes.length / 2);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0 && i % 2 == 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
