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
 * <p><b>Bao ve private key:</b> voi {@link KeyProtector} (client dung DPAPI tren Windows), file
 * luu dang version 2: {@code protection=dpapi}, {@code privateKeyProtected=<Base64>} - file bi chep
 * sang may / tai khoan khac cung khong dung duoc. Khong co protector thi luu version 1 nhu cu.
 */
public final class IdentityKeyStore {

    private static final String FORMAT_VERSION = "1";
    /** Version 2: private key duoc {@link KeyProtector} bao ve (vd DPAPI), khong con de tron. */
    private static final String PROTECTED_FORMAT_VERSION = "2";
    private static final String PROTECTION_PROPERTY = "protection";
    private static final String PROTECTED_PRIVATE_KEY_PROPERTY = "privateKeyProtected";
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
        return loadOrCreate(file, null);
    }

    /**
     * Nhu {@link #loadOrCreate(Path)} nhung private key duoc {@code protector} bao ve khi luu (dinh
     * dang version 2). File cu (version 1, private key de tron) tu dong duoc chuyen sang dang bao ve.
     *
     * @param protector null = luu private key dang thuong (version 1), vd tren he dieu hanh chua ho tro.
     */
    public static KeyPair loadOrCreate(Path file, KeyProtector protector) {
        if (Files.exists(file)) {
            Loaded loaded = load(file, protector);
            if (loaded.plaintext() && protector != null) {
                upgrade(file, loaded.keyPair(), protector);
            }
            return loaded.keyPair();
        }
        KeyPair created = IdentitySignatureService.generateIdentityKeyPair();
        try {
            saveNew(file, created, protector);
            return created;
        } catch (FileAlreadyExistsException e) {
            // Mot tien trinh khac (vd. mo 2 cua so app) vua tao file truoc minh -
            // dung danh tinh cua no thay vi ghi de.
            return load(file, protector).keyPair();
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the luu khoa danh tinh vao " + file, e);
        }
    }

    private record Loaded(KeyPair keyPair, boolean plaintext) {
    }

    private static Loaded load(Path file, KeyProtector protector) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong the doc file khoa danh tinh " + file, e);
        }

        String version = properties.getProperty(VERSION_PROPERTY);
        if (!FORMAT_VERSION.equals(version) && !PROTECTED_FORMAT_VERSION.equals(version)) {
            throw corrupted(file, "khong ho tro version=" + version, null);
        }
        boolean plaintext = FORMAT_VERSION.equals(version);

        KeyPair keyPair;
        try {
            PublicKey publicKey = IdentitySignatureService.decodePublicKey(
                    decodeBase64(properties, PUBLIC_KEY_PROPERTY));
            byte[] privateKeyBytes = plaintext
                    ? decodeBase64(properties, PRIVATE_KEY_PROPERTY)
                    : unprotect(file, properties, protector);
            PrivateKey privateKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            keyPair = new KeyPair(publicKey, privateKey);
        } catch (IllegalArgumentException | InvalidKeySpecException e) {
            throw corrupted(file, "khoa sai dinh dang", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuat toan EC khong kha dung", e);
        }

        if (!isMatchingPair(keyPair)) {
            throw corrupted(file, "public key va private key khong thuoc cung 1 cap", null);
        }
        return new Loaded(keyPair, plaintext);
    }

    private static byte[] unprotect(Path file, Properties properties, KeyProtector protector) {
        String protection = properties.getProperty(PROTECTION_PROPERTY);
        if (protector == null || !protector.name().equals(protection)) {
            throw new IllegalStateException("File khoa danh tinh " + file + " duoc bao ve bang '" + protection
                    + "' nhung may / he dieu hanh nay khong giai ma duoc. Khoa chi dung duoc tren dung tai khoan"
                    + " va dung may da tao ra no.");
        }
        try {
            return protector.unprotect(decodeBase64(properties, PROTECTED_PRIVATE_KEY_PROPERTY));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Khong giai ma duoc khoa danh tinh " + file
                    + " (file chep tu may hoac tai khoan Windows khac?). Khong tu ghi de de tranh mat danh tinh.", e);
        }
    }

    /** Chuyen file version 1 (private key de tron) sang version 2 (da bao ve), thay the nguyen tu. */
    private static void upgrade(Path file, KeyPair keyPair, KeyProtector protector) {
        Path directory = file.toAbsolutePath().getParent();
        try {
            Path tempFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
            try {
                write(tempFile, keyPair, protector);
                Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            // Khong nang cap duoc thi van dung khoa cu binh thuong - lan sau thu lai.
            System.getLogger(IdentityKeyStore.class.getName()).log(System.Logger.Level.WARNING,
                    "Khong chuyen duoc file khoa danh tinh sang dang ma hoa: " + e.getMessage());
        }
    }

    /**
     * Ghi ra file tam roi doi ten sang {@code file}: neu app bi tat giua chung
     * thi khong bao gio de lai 1 file danh tinh ghi do dang. Doi ten KHONG ghi
     * de - nem {@link FileAlreadyExistsException} neu {@code file} da co.
     */
    private static void saveNew(Path file, KeyPair keyPair, KeyProtector protector) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);

        Path tempFile = Files.createTempFile(directory, file.getFileName().toString(), ".tmp");
        try {
            write(tempFile, keyPair, protector);
            Files.move(tempFile, file);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void write(Path target, KeyPair keyPair, KeyProtector protector) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(PUBLIC_KEY_PROPERTY, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        if (protector == null) {
            properties.setProperty(VERSION_PROPERTY, FORMAT_VERSION);
            properties.setProperty(PRIVATE_KEY_PROPERTY, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        } else {
            properties.setProperty(VERSION_PROPERTY, PROTECTED_FORMAT_VERSION);
            properties.setProperty(PROTECTION_PROPERTY, protector.name());
            properties.setProperty(PROTECTED_PRIVATE_KEY_PROPERTY,
                    Base64.getEncoder().encodeToString(protector.protect(keyPair.getPrivate().getEncoded())));
        }
        restrictToOwner(target);
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            properties.store(writer, "Chat P2P Java - khoa danh tinh. KHONG chia se file nay.");
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
