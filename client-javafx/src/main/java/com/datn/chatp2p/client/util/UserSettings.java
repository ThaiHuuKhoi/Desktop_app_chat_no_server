package com.datn.chatp2p.client.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Cai dat ca nhan (Tai-lieu-ky-thuat.md muc E.8.6), luu o
 * {@code ~/.chat-p2p-java/settings.properties}. Khong chua bi mat. File hong/thieu thi dung mac
 * dinh, khong lam app sap. Chi doc/ghi tu JavaFX thread.
 */
public final class UserSettings {

    private static final Logger LOG = Logger.getLogger(UserSettings.class.getName());

    public static final String DEFAULT_THEME = "PRIMER_DARK";

    private static UserSettings instance;

    private final Path file;
    private String theme = DEFAULT_THEME;
    private boolean soundEnabled = true;
    private boolean desktopNotificationsEnabled = true;
    /** Luu lich su chat nhom tren may (ma hoa DPAPI) - mac dinh TAT, dung tinh than khong luu lai. */
    private boolean saveHistory = false;
    /**
     * Ten tai khoan dang nhap thanh cong gan nhat tren may (de hien "Tiếp tục là ..."), hoac null.
     * Chi luu TEN - khong luu mat khau hay token.
     */
    private String lastAccount;

    UserSettings(Path file) {
        this.file = file;
        load();
    }

    /** Cai dat cua nguoi dung hien tai (nap 1 lan). */
    public static synchronized UserSettings get() {
        if (instance == null) {
            instance = new UserSettings(Path.of(System.getProperty("user.home"), ".chat-p2p-java", "settings.properties"));
        }
        return instance;
    }

    public String theme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
        LOG.info(() -> "Cai dat: theme=" + theme);
        save();
    }

    public boolean soundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        LOG.info(() -> "Cai dat: am bao=" + (enabled ? "bat" : "tat"));
        save();
    }

    public boolean desktopNotificationsEnabled() {
        return desktopNotificationsEnabled;
    }

    public void setDesktopNotificationsEnabled(boolean enabled) {
        this.desktopNotificationsEnabled = enabled;
        LOG.info(() -> "Cai dat: thong bao man hinh=" + (enabled ? "bat" : "tat"));
        save();
    }

    public boolean saveHistory() {
        return saveHistory;
    }

    public void setSaveHistory(boolean enabled) {
        this.saveHistory = enabled;
        LOG.info(() -> "Cai dat: luu lich su tren may=" + (enabled ? "bat" : "tat"));
        save();
    }

    /** Ten tai khoan dung lan truoc, hoac null neu chua co / da quen. */
    public String lastAccount() {
        return lastAccount;
    }

    /** Ghi nho ten tai khoan vua dang nhap; null hoac rong = quen. */
    public void setLastAccount(String username) {
        this.lastAccount = username == null || username.isBlank() ? null : username.strip();
        LOG.info(() -> "Cai dat: tai khoan ghi nho=" + (lastAccount == null ? "(khong)" : lastAccount));
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Khong doc duoc " + file + ", dung cai dat mac dinh: " + e.getMessage());
            return;
        }
        theme = properties.getProperty("theme", DEFAULT_THEME);
        soundEnabled = Boolean.parseBoolean(properties.getProperty("sound", "true"));
        desktopNotificationsEnabled = Boolean.parseBoolean(properties.getProperty("notifications", "true"));
        saveHistory = Boolean.parseBoolean(properties.getProperty("saveHistory", "false"));
        String account = properties.getProperty("lastAccount", "").strip();
        lastAccount = account.isEmpty() ? null : account;
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty("theme", theme);
        properties.setProperty("sound", Boolean.toString(soundEnabled));
        properties.setProperty("notifications", Boolean.toString(desktopNotificationsEnabled));
        properties.setProperty("saveHistory", Boolean.toString(saveHistory));
        if (lastAccount != null) {
            properties.setProperty("lastAccount", lastAccount);
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "settings", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "Chat P2P Java - cai dat ca nhan");
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Khong luu duoc cai dat vao " + file + ": " + e.getMessage());
        }
    }
}
