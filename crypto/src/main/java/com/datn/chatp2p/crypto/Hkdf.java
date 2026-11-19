package com.datn.chatp2p.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;

/**
 * HKDF-SHA256 (RFC 5869): tach 1 bi mat chung (vd ket qua ECDH) thanh cac khoa
 * doc lap theo muc dich, thay vi bam SHA-256 truc tiep.
 *
 * <ul>
 *   <li><b>Extract</b>: {@code PRK = HMAC(salt, IKM)} - "lam deu" bi mat dau vao.</li>
 *   <li><b>Expand</b>: {@code OKM = T(1) || T(2) || ...}, {@code T(i) = HMAC(PRK, T(i-1) || info || i)} -
 *       moi gia tri {@code info} khac nhau cho ra 1 khoa khac nhau, khong suy ra duoc lan nhau.</li>
 * </ul>
 */
public final class Hkdf {

    private static final String HMAC = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    /** HKDF day du: extract roi expand ra {@code length} byte. */
    public static byte[] derive(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        return expand(extract(salt, inputKeyMaterial), info, length);
    }

    /** Buoc extract. {@code salt} rong/null thi dung chuoi 32 byte 0 (RFC 5869 muc 2.2). */
    public static byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        byte[] effectiveSalt = salt == null || salt.length == 0 ? new byte[HASH_LENGTH] : salt;
        return hmac(effectiveSalt, inputKeyMaterial);
    }

    /** Buoc expand: tu {@code prk} ra {@code length} byte (toi da 255 * 32). */
    public static byte[] expand(byte[] prk, byte[] info, int length) {
        if (length <= 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("Do dai HKDF khong hop le: " + length);
        }
        byte[] safeInfo = info == null ? new byte[0] : info;
        ByteArrayOutputStream output = new ByteArrayOutputStream(length);
        byte[] previous = new byte[0];
        for (int counter = 1; output.size() < length; counter++) {
            byte[] block = new byte[previous.length + safeInfo.length + 1];
            System.arraycopy(previous, 0, block, 0, previous.length);
            System.arraycopy(safeInfo, 0, block, previous.length, safeInfo.length);
            block[block.length - 1] = (byte) counter;
            previous = hmac(prk, block);
            output.write(previous, 0, Math.min(previous.length, length - output.size()));
        }
        return output.toByteArray();
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 khong kha dung", e);
        }
    }
}
