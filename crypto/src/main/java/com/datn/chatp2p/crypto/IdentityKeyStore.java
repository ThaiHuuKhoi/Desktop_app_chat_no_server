package com.datn.chatp2p.crypto;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Properties;

/**
 * Luu cap khoa danh tinh ECDSA ({@link IdentitySignatureService}) ra file de
 * dung lai qua cac lan mo ung dung - Tai-lieu-ky-thuat.md muc E.8.6.
 *
 * <p>Danh tinh chi co y nghia khi <b>cung 1 nguoi luon dung cung 1 khoa</b>:
 * neu moi lan mo app lai sinh khoa moi thi ke mao danh cung chi can tu sinh
 * khoa moi, va xac thuc {@code PEER_IDENTITY} mat tac dung chong mao danh lap lai.
 *
 * <p>Dinh dang file (java.util.Properties, UTF-8):
 * <pre>
 * version=1
 * publicKey=&lt;Base64 cua X.509&gt;
 * privateKey=&lt;Base64 cua PKCS#8&gt;
 * </pre>
 *
 * <p><b>Han che da biet:</b> private key duoc luu <b>chua ma hoa</b>, chi dua
 * vao quyen truy cap file cua he dieu hanh. Huong nang cap: ma hoa file bang
 * mat khau (PBKDF2 + {@link AesGcmCipher}) hoac dung kho khoa cua he dieu hanh.
 */
public final class IdentityKeyStore {

    private static final String FORMAT_VERSION = "1";
    private static final String VERSION_PROPERTY = "version";
    private static final String PUBLIC_KEY_PROPERTY = "publicKey";
    private static final String PRIVATE_KEY_PROPERTY = "privateKey";
    private static final String KEY_ALGORITHM = "EC";

    private IdentityKeyStore() {
    }

    /**
     * Doc cap khoa danh tinh tu {@code file}; neu file chua ton tai thi sinh
     * cap khoa moi, luu lai roi tra ve.
     *
     * <p>Neu file da ton tai nhung hong (sai dinh dang, khoa khong khop nhau),
     * nem {@link IllegalStateException} va <b>khong</b> ghi de - tu y sinh khoa
     * moi se lang le lam mat danh tinh cu cua nguoi dung.
     */
    public static KeyPair loadOrCreate(Path file) {
        if (Files.exists(file)) {
            return load(file);
        }
        KeyPair created = IdentitySignatureService.generateIdentityKeyPair();
        try {
            saveNew(file, created);
            return created;
        } catch (FileAlreadyExistsException e) {
            // Mot tien trinh khac (vd. mo 2 cua so app) vua tao file truoc minh -
            // dung danh tinh cua no thay vi ghi de.
            return load(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the luu khoa danh tinh vao " + file, e);
        }
    }

    private static KeyPair load(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the doc file khoa danh tinh " + file, e);
        }

        if (!FORMAT_VERSION.equals(properties.getProperty(VERSION_PROPERTY))) {
            throw corrupted(file, "khong ho tro version=" + properties.getProperty(VERSION_PROPERTY), null);
        }

        KeyPair keyPair;
        try {
            PublicKey publicKey = IdentitySignatureService.decodePublicKey(
                    decodeBase64(properties, PUBLIC_KEY_PROPERTY));
            PrivateKey privateKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeBase64(properties, PRIVATE_KEY_PROPERTY)));
            keyPair = new KeyPair(publicKey, privateKey);
        } catch (IllegalArgumentException | InvalidKeySpecException e) {
            throw corrupted(file, "khoa sai dinh dang", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuat toan EC khong kha dung", e);
        }

        if (!isMatchingPair(keyPair)) {
            throw corrupted(file, "public key va private key khong thuoc cung 1 cap", null);
        }
        return keyPair;
    }

    /**
     * Ghi ra file tam roi doi ten sang {@code file}: neu app bi tat giua chung
     * thi khong bao gio de lai 1 file danh tinh ghi do dang. Doi ten KHONG ghi
     * de - nem {@link FileAlreadyExistsException} neu {@code file} da co.
     */
    private static void saveNew(Path file, KeyPair keyPair) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);

        Properties properties = new Properties();
        properties.setProperty(VERSION_PROPERTY, FORMAT_VERSION);
        properties.setProperty(PUBLIC_KEY_PROPERTY, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        properties.setProperty(PRIVATE_KEY_PROPERTY, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));

        Path tempFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            restrictToOwner(tempFile);
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "Chat P2P Java - khoa danh tinh. KHONG chia se file nay.");
            }
            Files.move(tempFile, file);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /** Tren Linux/macOS chi cho chu so huu doc/ghi (600); Windows dua vao ACL thu muc nguoi dung. */
    private static void restrictToOwner(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static boolean isMatchingPair(KeyPair keyPair) {
        String probe = "identity-key-store-self-check";
        byte[] signature = IdentitySignatureService.sign(keyPair.getPrivate(), probe);
        return IdentitySignatureService.verify(keyPair.getPublic(), signature, probe);
    }

    private static byte[] decodeBase64(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thieu truong " + name);
        }
        return Base64.getDecoder().decode(value.trim());
    }

    private static IllegalStateException corrupted(Path file, String reason, Throwable cause) {
        return new IllegalStateException(
                "File khoa danh tinh bi hong (" + reason + "): " + file
                        + ". Khong tu ghi de de tranh mat danh tinh - hay kiem tra hoac xoa file thu cong.",
                cause);
    }
}
