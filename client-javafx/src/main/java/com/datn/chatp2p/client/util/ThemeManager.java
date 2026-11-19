package com.datn.chatp2p.client.util;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Supplier;

/**
 * Quan ly theme cua toan ung dung bang AtlantaFX - Tai-lieu-ky-thuat.md muc E.8.6.
 *
 * <p>Theme dat bang {@link Application#setUserAgentStylesheet}, nen ap dung ngay cho MOI cua so
 * dang mo, khong can nap lai FXML. {@code app.css} chi dung bien mau cua AtlantaFX
 * ({@code -color-...}) nen tu doi mau theo theme. Lua chon duoc luu trong {@link UserSettings}.
 */
public final class ThemeManager {

    /** Cac bo theme co san cua AtlantaFX; moi bo co cap sang/toi (tru Dracula chi co toi). */
    public enum AppTheme {
        PRIMER_DARK("Primer tối", true, PrimerDark::new),
        PRIMER_LIGHT("Primer sáng", false, PrimerLight::new),
        NORD_DARK("Nord tối", true, NordDark::new),
        NORD_LIGHT("Nord sáng", false, NordLight::new),
        CUPERTINO_DARK("Cupertino tối", true, CupertinoDark::new),
        CUPERTINO_LIGHT("Cupertino sáng", false, CupertinoLight::new),
        DRACULA("Dracula", true, Dracula::new);

        private final String label;
        private final boolean dark;
        private final Supplier<Theme> factory;

        AppTheme(String label, boolean dark, Supplier<Theme> factory) {
            this.label = label;
            this.dark = dark;
            this.factory = factory;
        }

        public boolean isDark() {
            return dark;
        }

        /** Ban sang/toi cung ho (Dracula khong co ban sang -&gt; Primer sang). */
        AppTheme counterpart() {
            return switch (this) {
                case PRIMER_DARK -> PRIMER_LIGHT;
                case PRIMER_LIGHT -> PRIMER_DARK;
                case NORD_DARK -> NORD_LIGHT;
                case NORD_LIGHT -> NORD_DARK;
                case CUPERTINO_DARK -> CUPERTINO_LIGHT;
                case CUPERTINO_LIGHT -> CUPERTINO_DARK;
                case DRACULA -> PRIMER_LIGHT;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static AppTheme current = parse(UserSettings.get().theme());

    private ThemeManager() {
    }

    /** Ap dung theme hien tai. Goi 1 lan luc khoi dong app. */
    public static void applyCurrentTheme() {
        Application.setUserAgentStylesheet(current.factory.get().getUserAgentStylesheet());
    }

    public static AppTheme current() {
        return current;
    }

    /** Doi theme, ap dung ngay va luu lai. */
    public static void setTheme(AppTheme theme) {
        current = theme;
        applyCurrentTheme();
        UserSettings.get().setTheme(theme.name());
    }

    /** Bien {@code button} thanh nut chuyen nhanh sang/toi (giu nguyen bo theme). */
    public static void installToggle(Button button) {
        updateToggleIcon(button);
        button.setOnAction(event -> {
            setTheme(current.counterpart());
            updateToggleIcon(button);
        });
    }

    private static void updateToggleIcon(Button button) {
        boolean dark = current.isDark();
        button.setGraphic(new FontIcon(dark ? Feather.SUN : Feather.MOON));
        button.setTooltip(new Tooltip(dark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"));
    }

    private static AppTheme parse(String name) {
        try {
            return AppTheme.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return AppTheme.PRIMER_DARK;
        }
    }
}
