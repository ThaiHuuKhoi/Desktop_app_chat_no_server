package com.datn.chatp2p.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWhenFileMissing() {
        UserSettings settings = new UserSettings(dir.resolve("settings.properties"));
        assertEquals(UserSettings.DEFAULT_THEME, settings.theme());
        assertTrue(settings.soundEnabled());
        assertTrue(settings.desktopNotificationsEnabled());
    }

    @Test
    void changesPersistAcrossInstances() {
        Path file = dir.resolve("nested").resolve("settings.properties");
        UserSettings settings = new UserSettings(file);
        settings.setTheme("NORD_LIGHT");
        settings.setSoundEnabled(false);
        settings.setDesktopNotificationsEnabled(false);

        UserSettings reloaded = new UserSettings(file);
        assertEquals("NORD_LIGHT", reloaded.theme());
        assertFalse(reloaded.soundEnabled());
        assertFalse(reloaded.desktopNotificationsEnabled());
    }

    @Test
    void corruptFileFallsBackToDefaults() throws Exception {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "sound=\\u12");   // escape unicode hong -> Properties.load nem loi
        UserSettings settings = new UserSettings(file);
        assertTrue(settings.soundEnabled());
    }

    @Test
    void onlyHttpLinksAreOpened() {
        assertTrue(LinkOpener.isSafe("https://example.com"));
        assertTrue(LinkOpener.isSafe("http://example.com"));
        assertFalse(LinkOpener.isSafe("file:///C:/Windows/System32/cmd.exe"));
        assertFalse(LinkOpener.isSafe("javascript:alert(1)"));
        assertFalse(LinkOpener.isSafe("not a url"));
    }
}
