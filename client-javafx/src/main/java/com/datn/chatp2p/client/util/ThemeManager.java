package com.datn.chatp2p.client.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Quan ly theme sang/toi cua toan ung dung bang AtlantaFX (bo Primer) -
 * Tai-lieu-ky-thuat.md muc E.8.6. Mac dinh: toi.
 *
 * <p>Theme dat bang {@link Application#setUserAgentStylesheet}, nen ap dung
 * ngay cho MOI cua so dang mo, khong can nap lai FXML. {@code app.css} chi
 * dung bien mau cua AtlantaFX ({@code -color-...}) nen tu doi mau theo theme.
 *
 * <p>Chua luu lua chon qua cac lan mo app - se lam cung cai dat ca nhan.
 */
public final class ThemeManager {

    private static boolean dark = true;

    private ThemeManager() {
    }

    /** Ap dung theme hien tai. Goi 1 lan luc khoi dong app. */
    public static void applyCurrentTheme() {
        Theme theme = dark ? new PrimerDark() : new PrimerLight();
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
    }

    /** Bien {@code button} thanh nut chuyen theme: icon mat trang/mat troi, bam de doi. */
    public static void installToggle(Button button) {
        updateToggleIcon(button);
        button.setOnAction(event -> {
            dark = !dark;
            applyCurrentTheme();
            updateToggleIcon(button);
        });
    }

    private static void updateToggleIcon(Button button) {
        button.setGraphic(new FontIcon(dark ? Feather.SUN : Feather.MOON));
        button.setTooltip(new Tooltip(dark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"));
    }
}
