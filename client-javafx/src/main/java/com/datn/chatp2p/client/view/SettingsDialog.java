package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.util.SoundPlayer;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.client.util.ThemeManager.AppTheme;
import com.datn.chatp2p.client.util.UserSettings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Hop thoai cai dat ca nhan (Tai-lieu-ky-thuat.md muc E.8.6): theme, am bao, thong bao man hinh.
 * Thay doi ap dung + luu NGAY khi bam (khong can nut Luu) - chon theme la thay giao dien doi luon.
 */
final class SettingsDialog {

    private SettingsDialog() {
    }

    /** {@code onThemeChanged} chay sau khi doi theme (vd cap nhat icon nut sang/toi). */
    static void show(Window owner, Runnable onThemeChanged) {
        UserSettings settings = UserSettings.get();

        ComboBox<AppTheme> themeBox = new ComboBox<>();
        themeBox.getItems().setAll(AppTheme.values());
        themeBox.setValue(ThemeManager.current());
        themeBox.setMaxWidth(Double.MAX_VALUE);
        themeBox.valueProperty().addListener((obs, old, theme) -> {
            if (theme != null) {
                ThemeManager.setTheme(theme);
                onThemeChanged.run();
            }
        });

        CheckBox sound = new CheckBox("Phát âm báo khi có tin nhắn mới");
        sound.setSelected(settings.soundEnabled());
        sound.selectedProperty().addListener((obs, old, on) -> settings.setSoundEnabled(on));
        Button testSound = new Button("Nghe thử", new FontIcon(Feather.VOLUME_2));
        testSound.getStyleClass().add("flat");
        testSound.setOnAction(event -> SoundPlayer.playIncoming());
        HBox soundRow = new HBox(8, sound, testSound);
        soundRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        CheckBox notifications = new CheckBox("Hiện thông báo trên màn hình khi cửa sổ không được chọn");
        notifications.setWrapText(true);
        notifications.setSelected(settings.desktopNotificationsEnabled());
        notifications.selectedProperty().addListener((obs, old, on) -> settings.setDesktopNotificationsEnabled(on));
        Label privacy = new Label("Thông báo chỉ hiện tên người gửi, không hiện nội dung tin nhắn.");
        privacy.setWrapText(true);
        privacy.getStyleClass().add("settings-hint");

        Label themeTitle = new Label("Giao diện");
        themeTitle.getStyleClass().add("settings-section");
        Label alertTitle = new Label("Thông báo");
        alertTitle.getStyleClass().add("settings-section");

        VBox content = new VBox(10, themeTitle, themeBox, alertTitle, soundRow, notifications, privacy);
        content.setPadding(new Insets(4, 4, 4, 4));
        content.setPrefWidth(380);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Cài đặt");
        dialog.setHeaderText("Cài đặt cá nhân");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Đóng", ButtonType.CLOSE.getButtonData()));
        Navigator.style(dialog.getDialogPane());
        dialog.showAndWait();
    }
}
