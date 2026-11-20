package com.datn.chatp2p.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

/**
 * Sao luu / chuyen khoa danh tinh sang may khac: file xuat ra duoc ma hoa bang MAT KHAU do nguoi
 * dung dat (PBKDF2-HMAC-SHA256 {@value #ITERATIONS} vong, salt ngau nhien 16 byte -&gt; AES-256-GCM).
 * Khac voi {@link IdentityKeyStore} (DPAPI, chi mo duoc tren dung may): file nay mo duoc o bat ky
 * may nao, mien la biet mat khau.
 *
 * <p>Dinh dang (Properties, UTF-8): {@code format=chatp2p-identity-backup-1}, {@code kdf},
 * {@code iterations}, {@code salt}, {@code publicKey}, {@code privateKeyEncrypted} (IV || ciphertext+tag).
 */
public final class IdentityBackup {

    static final int ITERATIONS = 310_000;
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final String FORMAT = "chatp2p-identity-backup-1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdentityBackup() {
    }

    /** Ma hoa cap khoa thanh noi dung file sao luu. */
    public static byte[] export(KeyPair keyPair, char[] password) {
        requireStrong(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        SecretKeySpec key = deriveKey(password, salt, ITERATIONS);
        Properties properties = new Properties();
        properties.setProperty("format", FORMAT);
        properties.setProperty("kdf", "PBKDF2WithHmacSHA256");
        properties.setProperty("iterations", Integer.toString(ITERATIONS));
        properties.setProperty("salt", Base64.getEncoder().encodeToString(salt));
        properties.setProperty("publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        properties.setProperty("privateKeyEncrypted",
                Base64.getEncoder().encodeToString(AesGcmCipher.encrypt(key, keyPair.getPrivate().getEncoded())));
        StringWriter writer = new StringWriter();
        try {
            properties.store(writer, "Chat P2P Java - ban sao khoa danh tinh (ma hoa bang mat khau). Giu bi mat.");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Giai ma file sao luu.
     *
     * @throws IllegalArgumentException sai dinh dang, sai mat khau, hoac file bi sua.
     */
    public static KeyPair importBackup(byte[] content, char[] password) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(new String(content, StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("File không phải bản sao khoá danh tính", e);
        }
        if (!FORMAT.equals(properties.getProperty("format"))) {
            throw new IllegalArgumentException("File không phải bản sao khoá danh tính");
        }
        try {
            int iterations = Integer.parseInt(properties.getProperty("iterations"));
            if (iterations < 100_000 || iterations > 10_000_000) {
                throw new IllegalArgumentException("Thông số mã hoá không hợp lệ");
            }
            byte[] salt = Base64.getDecoder().decode(properties.getProperty("salt"));
            PublicKey publicKey = IdentitySignatureService.decodePublicKey(
                    Base64.getDecoder().decode(properties.getProperty("publicKey")));
            byte[] privateBytes;
            try {
                privateBytes = AesGcmCipher.decrypt(deriveKey(password, salt, iterations),
                        Base64.getDecoder().decode(properties.getProperty("privateKeyEncrypted")));
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("Sai mật khẩu hoặc file đã bị sửa", e);
            }
            PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            String probe = "identity-backup-check";
            if (!IdentitySignatureService.verify(publicKey, IdentitySignatureService.sign(privateKey, probe), probe)) {
                throw new IllegalArgumentException("Khoá trong file không khớp nhau");
            }
            return keyPair;
        } catch (GeneralSecurityException | NullPointerException | NumberFormatException e) {
            throw new IllegalArgumentException("File sao lưu bị hỏng", e);
        }
    }

    private static void requireStrong(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu cần ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }

    private static SecretKeySpec deriveKey(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 khong kha dung", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Xoa mat khau khoi bo nho sau khi dung. */
    public static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
