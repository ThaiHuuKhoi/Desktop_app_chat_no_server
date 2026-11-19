package com.datn.chatp2p.client.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Danh sach nguoi da duoc xac minh van tay (giong "Safety number da xac minh" cua Signal), luu o
 * {@code ~/.chat-p2p-java/trusted-peers.properties}: ten hien thi -&gt; van tay khoa danh tinh.
 *
 * <p>Chi luu khi nguoi dung CHU DONG so van tay va bam "Khop". Lan sau gap lai cung ten:
 * <ul>
 *   <li>cung van tay -&gt; {@link Status#TRUSTED} ("Da tin cay");</li>
 *   <li>khac van tay -&gt; {@link Status#CHANGED}: canh bao, co the la nguoi khac mao danh cung ten
 *       (hoac nguoi do doi may / tao lai khoa).</li>
 * </ul>
 * Khong tu dong luu moi nguoi gap lan dau, vi ten o che do khach la tu dat va hay trung - tu dong
 * ghim se sinh canh bao gia.
 */
public final class TrustedPeers {

    public enum Status {
        /** Chua tung xac minh nguoi ten nay. */
        UNKNOWN,
        /** Van tay khop voi lan da xac minh. */
        TRUSTED,
        /** Cung ten nhung van tay KHAC lan da xac minh. */
        CHANGED
    }

    private static final Logger LOG = Logger.getLogger(TrustedPeers.class.getName());
    private static TrustedPeers instance;

    private final Path file;
    private final Properties entries = new Properties();

    TrustedPeers(Path file) {
        this.file = file;
        load();
    }

    public static synchronized TrustedPeers get() {
        if (instance == null) {
            instance = new TrustedPeers(Path.of(System.getProperty("user.home"), ".chat-p2p-java", "trusted-peers.properties"));
        }
        return instance;
    }

    public synchronized Status status(String displayName, String fingerprint) {
        if (displayName == null || fingerprint == null) {
            return Status.UNKNOWN;
        }
        String saved = entries.getProperty(key(displayName));
        if (saved == null) {
            return Status.UNKNOWN;
        }
        return normalize(saved).equals(normalize(fingerprint)) ? Status.TRUSTED : Status.CHANGED;
    }

    /** Ghi nho van tay cua nguoi nay sau khi nguoi dung xac nhan khop (ghi de van tay cu neu co). */
    public synchronized void trust(String displayName, String fingerprint) {
        entries.setProperty(key(displayName), normalize(fingerprint));
        LOG.info(() -> "Da tin cay van tay cua '" + displayName + "'");
        save();
    }

    /** Quen nguoi nay (vd nguoi dung xac nhan van tay KHONG khop). */
    public synchronized void forget(String displayName) {
        if (entries.remove(key(displayName)) != null) {
            save();
        }
    }

    private static String key(String displayName) {
        return displayName.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String fingerprint) {
        return fingerprint.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            entries.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Khong doc duoc " + file + ": " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "trusted-peers", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                entries.store(writer, "Chat P2P Java - nguoi da xac minh van tay (ten -> van tay)");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Khong luu duoc " + file + ": " + e.getMessage());
        }
    }
}
