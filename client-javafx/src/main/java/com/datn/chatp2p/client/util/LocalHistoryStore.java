package com.datn.chatp2p.client.util;

import com.datn.chatp2p.crypto.KeyProtector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Lich su chat NHOM luu tren may (tuy chon, mac dinh tat - xem {@link UserSettings#saveHistory()}).
 *
 * <ul>
 *   <li>Moi phong 1 file {@code ~/.chat-p2p-java/history/<SHA-256(khoa phong)>.dat}: ten file khong
 *       lo ten phong; khoa phong gom ca "the" mat khau nen 2 phong cung ten khac mat khau khong lan.</li>
 *   <li>Noi dung (JSON) duoc {@link KeyProtector} (DPAPI) ma hoa - chi dung tai khoan Windows do mo
 *       duoc. Khong co bo ma hoa thi KHONG luu (khong de lich su dang thuong).</li>
 *   <li>Peer id doi moi lan vao phong, nen luu ten nguoi viet va co "tin cua minh".</li>
 * </ul>
 */
public final class LocalHistoryStore {

    /** 1 tin da luu. */
    public record SavedMessage(String id, boolean mine, String authorName, String text, long timeSent, String replyToId) {
    }

    /** Giu toi da bay nhieu tin gan nhat moi phong. */
    public static final int MAX_MESSAGES = 500;
    private static final Logger LOG = Logger.getLogger(LocalHistoryStore.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path directory;
    private final KeyProtector protector;

    public LocalHistoryStore(Path directory, KeyProtector protector) {
        this.directory = directory;
        this.protector = protector;
    }

    /** Kho mac dinh {@code ~/.chat-p2p-java/history}, DPAPI tren Windows (null o HDH khac -&gt; khong luu). */
    public static LocalHistoryStore forCurrentUser() {
        return new LocalHistoryStore(Path.of(System.getProperty("user.home"), ".chat-p2p-java", "history"),
                DpapiKeyProtector.forCurrentPlatform());
    }

    /** May nay co ma hoa duoc khong (khong thi tinh nang luu lich su bi tat). */
    public boolean available() {
        return protector != null;
    }

    public void save(String roomKey, List<SavedMessage> messages) {
        if (protector == null) {
            return;
        }
        List<SavedMessage> recent = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size())
                : messages;
        try {
            Files.createDirectories(directory);
            byte[] encrypted = protector.protect(JSON.writeValueAsBytes(recent));
            Path temp = Files.createTempFile(directory, "history", ".tmp");
            Files.write(temp, encrypted);
            Files.move(temp, fileFor(roomKey), StandardCopyOption.REPLACE_EXISTING);
            LOG.info(() -> "Da luu " + recent.size() + " tin cua phong vao may (ma hoa)");
        } catch (IOException | RuntimeException e) {
            LOG.warning(() -> "Khong luu duoc lich su: " + e.getMessage());
        }
    }

    /** Rong neu chua luu, khong giai ma duoc (vd file tu tai khoan khac) hoac khong co bo ma hoa. */
    public List<SavedMessage> load(String roomKey) {
        Path file = fileFor(roomKey);
        if (protector == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            byte[] json = protector.unprotect(Files.readAllBytes(file));
            return JSON.readValue(json, new TypeReference<List<SavedMessage>>() { });
        } catch (IOException | RuntimeException e) {
            LOG.warning(() -> "Khong doc duoc lich su da luu: " + e.getMessage());
            return List.of();
        }
    }

    /** Xoa toan bo lich su da luu. @return so file da xoa. */
    public int deleteAll() {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".dat")).toList()) {
                Files.deleteIfExists(file);
                deleted++;
            }
        } catch (IOException e) {
            LOG.warning(() -> "Khong xoa het duoc lich su: " + e.getMessage());
        }
        return deleted;
    }

    Path fileFor(String roomKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(roomKey.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".dat");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 khong kha dung", e);
        }
    }
}
