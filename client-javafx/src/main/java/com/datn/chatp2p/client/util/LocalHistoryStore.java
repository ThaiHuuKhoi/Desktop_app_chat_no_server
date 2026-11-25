package com.datn.chatp2p.client.util;

import com.datn.chatp2p.crypto.KeyProtector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
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
 *   <li>Tu 25/11 file con ghi (ben trong phan da ma hoa) ten phong, co mat khau khong, tai khoan da luu
 *       va thoi diem luu - de sanh hien muc "Phong da luu" ({@link #listRooms}). Truoc day file chi co danh
 *       sach tin, nen phong bien mat khoi server la nguoi dung phai tu nho ten phong. KHONG luu mat khau
 *       phong. File dinh dang cu (chi co danh sach tin) van doc duoc, nhung khong hien trong danh sach.</li>
 * </ul>
 */
public final class LocalHistoryStore {

    /** 1 tin da luu. */
    public record SavedMessage(String id, boolean mine, String authorName, String text, long timeSent, String replyToId) {
    }

    /**
     * 1 phong co lich su luu tren may, cho muc "Phong da luu" o sanh.
     *
     * @param roomKey  khoa phong ({@code ten} hoac {@code ten~the}) - dung de xoa; khong chua mat khau
     * @param owner    tai khoan da luu ("" = khach)
     */
    public record SavedRoom(String roomKey, String roomName, boolean passwordProtected, String owner,
                            long savedAt, int messageCount) {
    }

    /** Noi dung 1 file (dinh dang 2). */
    private record StoredHistory(int version, String roomKey, String roomName, boolean passwordProtected,
                                 String owner, long savedAt, List<SavedMessage> messages) {
    }

    private static final int FORMAT_VERSION = 2;
    /** Gioi han so file doc khi liet ke, de sanh khong bi cham neu thu muc co rat nhieu file. */
    static final int MAX_LISTED_FILES = 300;

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

    /** Luu khong kem thong tin phong - phong nay se khong hien trong muc "Phong da luu". */
    public void save(String roomKey, List<SavedMessage> messages) {
        save(roomKey, null, false, "", messages);
    }

    /**
     * Luu tin nhom cua phong {@code roomKey}, kem ten phong / co mat khau / tai khoan de hien o sanh.
     *
     * @param roomName ten phong hien cho nguoi dung (null = khong hien trong danh sach)
     * @param owner    tai khoan dang dang nhap ("" = khach)
     */
    public void save(String roomKey, String roomName, boolean passwordProtected, String owner,
                     List<SavedMessage> messages) {
        if (protector == null) {
            return;
        }
        List<SavedMessage> recent = messages.size() > MAX_MESSAGES
                ? messages.subList(messages.size() - MAX_MESSAGES, messages.size())
                : messages;
        try {
            Files.createDirectories(directory);
            StoredHistory stored = new StoredHistory(FORMAT_VERSION, roomKey, roomName, passwordProtected,
                    owner == null ? "" : owner, System.currentTimeMillis(), List.copyOf(recent));
            byte[] encrypted = protector.protect(JSON.writeValueAsBytes(stored));
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
            return read(file).messages();
        } catch (IOException | RuntimeException e) {
            LOG.warning(() -> "Khong doc duoc lich su da luu: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Cac phong co lich su da luu boi tai khoan {@code owner}, moi luu nhat truoc. Chi gom file dinh dang moi
     * (co ten phong). Loc theo tai khoan vi thu muc dung chung cho moi tai khoan dang nhap tren cung tai khoan
     * Windows - nguoi khac dang nhap tren may nay khong thay phong cua minh.
     */
    public List<SavedRoom> listRooms(String owner) {
        if (protector == null || owner == null || owner.isBlank() || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<SavedRoom> rooms = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".dat")).limit(MAX_LISTED_FILES).toList()) {
                try {
                    StoredHistory stored = read(file);
                    if (stored.roomName() != null && stored.roomKey() != null && owner.equals(stored.owner())) {
                        rooms.add(new SavedRoom(stored.roomKey(), stored.roomName(), stored.passwordProtected(),
                                stored.owner(), stored.savedAt(), stored.messages().size()));
                    }
                } catch (IOException | RuntimeException e) {
                    // File hong / cua tai khoan Windows khac - bo qua file do.
                }
            }
        } catch (IOException e) {
            LOG.warning(() -> "Khong liet ke duoc lich su da luu: " + e.getMessage());
        }
        rooms.sort(Comparator.comparingLong(SavedRoom::savedAt).reversed());
        return rooms;
    }

    /** Xoa lich su da luu cua 1 phong. @return {@code true} neu da xoa. */
    public boolean delete(String roomKey) {
        try {
            return Files.deleteIfExists(fileFor(roomKey));
        } catch (IOException e) {
            LOG.warning(() -> "Khong xoa duoc lich su phong: " + e.getMessage());
            return false;
        }
    }

    /** Giai ma va doc 1 file: dinh dang 2 (co thong tin phong) hoac dinh dang cu (chi danh sach tin). */
    private StoredHistory read(Path file) throws IOException {
        byte[] json = protector.unprotect(Files.readAllBytes(file));
        JsonNode root = JSON.readTree(json);
        if (root.isArray()) {
            List<SavedMessage> messages = JSON.convertValue(root, new TypeReference<List<SavedMessage>>() { });
            return new StoredHistory(1, null, null, false, "", 0, messages);
        }
        StoredHistory stored = JSON.treeToValue(root, StoredHistory.class);
        return stored.messages() == null
                ? new StoredHistory(stored.version(), stored.roomKey(), stored.roomName(), stored.passwordProtected(),
                        stored.owner(), stored.savedAt(), List.of())
                : stored;
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
