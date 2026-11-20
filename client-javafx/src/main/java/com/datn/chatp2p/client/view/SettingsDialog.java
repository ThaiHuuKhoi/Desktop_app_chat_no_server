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

    /** Xuat khoa danh tinh ra file, ma hoa bang mat khau nguoi dung dat (nhap 2 lan). */
    private static void exportIdentity(Window owner) {
        java.util.Optional<char[]> password = askPassword(owner, "Đặt mật khẩu cho file khoá", true);
        if (password.isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Lưu file khoá danh tính");
        chooser.setInitialFileName("chatp2p-identity.backup");
        java.io.File target = chooser.showSaveDialog(owner);
        if (target == null) {
            com.datn.chatp2p.crypto.IdentityBackup.wipe(password.get());
            return;
        }
        try {
            byte[] backup = com.datn.chatp2p.crypto.IdentityBackup.export(
                    com.datn.chatp2p.client.session.PeerIdentityService.loadIdentity(), password.get());
            java.nio.file.Files.write(target.toPath(), backup);
            info(owner, "Đã xuất khoá", "Đã lưu vào " + target + ".\nGiữ file và mật khẩu cẩn thận: ai có cả hai "
                    + "có thể mạo danh bạn.");
        } catch (RuntimeException | java.io.IOException e) {
            info(owner, "Không xuất được khoá", e.getMessage());
        } finally {
            com.datn.chatp2p.crypto.IdentityBackup.wipe(password.get());
        }
    }

    /**
     * Nhap khoa tu file sao luu, thay danh tinh tren may nay (giu ban cu). Goi tu Cai dat hoac tu man
     * hinh dang nhap khi bao "khoa khong khop tai khoan" (dang nhap tren may moi).
     *
     * @return {@code true} neu da thay danh tinh.
     */
    static boolean importIdentity(Window owner) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Chọn file khoá danh tính");
        java.io.File source = chooser.showOpenDialog(owner);
        if (source == null) {
            return false;
        }
        java.util.Optional<char[]> password = askPassword(owner, "Mật khẩu của file khoá", false);
        if (password.isEmpty()) {
            return false;
        }
        try {
            java.security.KeyPair imported = com.datn.chatp2p.crypto.IdentityBackup.importBackup(
                    java.nio.file.Files.readAllBytes(source.toPath()), password.get());
            java.nio.file.Path old = com.datn.chatp2p.client.session.PeerIdentityService.replaceIdentity(imported);
            info(owner, "Đã nhập khoá", "Danh tính đã được thay. Nếu đang ở trong phòng, hãy thoát và mở lại "
                    + "ứng dụng để dùng danh tính mới."
                    + (old == null ? "" : "\nDanh tính cũ được giữ lại ở " + old + "."));
            return true;
        } catch (RuntimeException | java.io.IOException e) {
            info(owner, "Không nhập được khoá", e.getMessage());
            return false;
        } finally {
            com.datn.chatp2p.crypto.IdentityBackup.wipe(password.get());
        }
    }

    private static java.util.Optional<char[]> askPassword(Window owner, String header, boolean confirm) {
        javafx.scene.control.PasswordField first = new javafx.scene.control.PasswordField();
        first.setPromptText("Mật khẩu (ít nhất " + com.datn.chatp2p.crypto.IdentityBackup.MIN_PASSWORD_LENGTH + " ký tự)");
        javafx.scene.control.PasswordField second = new javafx.scene.control.PasswordField();
        second.setPromptText("Nhập lại mật khẩu");
        Label error = new Label();
        error.getStyleClass().add("error-label");
        VBox box = new VBox(8, first);
        if (confirm) {
            box.getChildren().add(second);
        }
        box.getChildren().add(error);
        box.setPrefWidth(340);
        ButtonType ok = new ButtonType("Tiếp tục", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        Dialog<char[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Mật khẩu");
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ok).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String message = first.getText().length() < com.datn.chatp2p.crypto.IdentityBackup.MIN_PASSWORD_LENGTH
                    ? "Mật khẩu cần ít nhất " + com.datn.chatp2p.crypto.IdentityBackup.MIN_PASSWORD_LENGTH + " ký tự"
                    : confirm && !first.getText().equals(second.getText()) ? "Hai lần nhập không khớp" : null;
            if (message != null) {
                error.setText(message);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ok ? first.getText().toCharArray() : null);
        Navigator.style(dialog.getDialogPane());
        javafx.application.Platform.runLater(first::requestFocus);
        return dialog.showAndWait();
    }

    private static void info(Window owner, String header, String text) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, text);
        alert.initOwner(owner);
        alert.setHeaderText(header);
        Navigator.style(alert.getDialogPane());
        alert.showAndWait();
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

        Label historyTitle = new Label("Lịch sử");
        historyTitle.getStyleClass().add("settings-section");
        com.datn.chatp2p.client.util.LocalHistoryStore store = com.datn.chatp2p.client.util.LocalHistoryStore.forCurrentUser();
        CheckBox saveHistory = new CheckBox("Lưu tin nhắn nhóm trên máy này (mã hoá), mở lại phòng sẽ thấy tin cũ");
        saveHistory.setWrapText(true);
        saveHistory.setSelected(settings.saveHistory() && store.available());
        saveHistory.setDisable(!store.available());
        saveHistory.selectedProperty().addListener((obs, old, on) -> settings.setSaveHistory(on));
        Label historyHint = new Label(store.available()
                ? "Mặc định tắt: rời phòng là tin nhắn biến mất. Khi bật, tin được mã hoá bằng tài khoản Windows của bạn; tin riêng không được lưu."
                : "Máy này không hỗ trợ mã hoá lịch sử (chỉ có trên Windows), nên không lưu.");
        historyHint.setWrapText(true);
        historyHint.getStyleClass().add("settings-hint");
        Button clearHistory = new Button("Xoá lịch sử đã lưu", new FontIcon(Feather.TRASH_2));
        clearHistory.getStyleClass().add("flat");
        clearHistory.setOnAction(event -> {
            int deleted = store.deleteAll();
            clearHistory.setText(deleted == 0 ? "Không có lịch sử nào" : "Đã xoá lịch sử của " + deleted + " phòng");
            clearHistory.setDisable(true);
        });

        Label identityTitle = new Label("Danh tính (chuyển sang máy khác)");
        identityTitle.getStyleClass().add("settings-section");
        Label identityHint = new Label("Khoá danh tính chỉ mở được trên máy này. Muốn dùng tài khoản trên máy mới, "
                + "xuất khoá ra file (bảo vệ bằng mật khẩu) rồi nhập ở máy mới.");
        identityHint.setWrapText(true);
        identityHint.getStyleClass().add("settings-hint");
        Button exportIdentity = new Button("Xuất khoá…", new FontIcon(Feather.UPLOAD));
        exportIdentity.setOnAction(event -> exportIdentity(owner));
        Button importIdentity = new Button("Nhập khoá…", new FontIcon(Feather.DOWNLOAD));
        importIdentity.setOnAction(event -> importIdentity(owner));
        HBox identityRow = new HBox(8, exportIdentity, importIdentity);

        VBox content = new VBox(10, themeTitle, themeBox, alertTitle, soundRow, notifications, privacy,
                historyTitle, saveHistory, historyHint, clearHistory, identityTitle, identityHint, identityRow);
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
