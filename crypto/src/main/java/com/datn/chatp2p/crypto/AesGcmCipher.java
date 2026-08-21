package com.datn.chatp2p.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Ma hoa/giai ma noi dung (tin nhan, chunk file) bang AES-256-GCM, dung khoa
 * phien sinh tu {@link KeyExchangeService#deriveSharedSecret}
 * (De-cuong-Chat-P2P-Java.md muc 5).
 *
 * <p>Dinh dang output cua {@link #encrypt}: {@code IV (12 byte) || ciphertext+tag},
 * tu chua het thong tin can thiet de {@link #decrypt} khong can truyen IV rieng.
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCipher() {
    }

    public static byte[] encrypt(SecretKey key, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            return ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ma hoa AES-GCM that bai", e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] ivAndCiphertext) {
        if (ivAndCiphertext.length < IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Du lieu qua ngan, thieu IV");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ivAndCiphertext);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Giai ma AES-GCM that bai (sai khoa hoac du lieu bi thay doi)", e);
        }
    }
}
