package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.util.RoomNameGenerator;
import com.datn.chatp2p.client.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.security.SecureRandom;

/**
 * Man hinh dau tien: nhap ten hien thi va ten phong roi vao phong
 * (De-cuong-Chat-P2P-Java.md muc 5 - "tao/tham gia phong chat"). Khong co
 * dang nhap/tai khoan - "danh tinh" chi la ten hien thi + khoa sinh tren may.
 */
public class HomeController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_SERVER_URI = "ws://localhost:8080";

    @FXML
    private StackPane avatarHolder;

    @FXML
    private TextField userNameField;

    @FXML
    private TextField roomNameField;

    @FXML
    private Button copyRoomButton;

    @FXML
    private TextField serverField;

    @FXML
    private CheckBox demoModeCheckBox;

    @FXML
    private Label errorLabel;

    @FXML
    private Button themeToggleButton;

    private Stage stage;

    @FXML
    private void initialize() {
        userNameField.textProperty().addListener((obs, old, name) -> refreshAvatar(name));
        userNameField.setText("Khách-" + (1000 + RANDOM.nextInt(9000)));
        roomNameField.setText(RoomNameGenerator.generate());
        serverField.setText(DEFAULT_SERVER_URI);
        demoModeCheckBox.setSelected(true);
        serverField.disableProperty().bind(demoModeCheckBox.selectedProperty());

        ThemeManager.installToggle(themeToggleButton);
        // Chi chiem cho trong the khi thuc su co loi de hien
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.visibleProperty().bind(errorLabel.managedProperty());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void onGenerateRoomName() {
        roomNameField.setText(RoomNameGenerator.generate());
    }

    @FXML
    private void onCopyRoomName() {
        ClipboardContent content = new ClipboardContent();
        content.putString(roomNameField.getText());
        Clipboard.getSystemClipboard().setContent(content);

        // Doi icon thanh dau tich trong chop mat de nguoi dung biet da sao chep
        copyRoomButton.setGraphic(new FontIcon(Feather.CHECK));
        PauseTransition revert = new PauseTransition(Duration.seconds(1.5));
        revert.setOnFinished(e -> copyRoomButton.setGraphic(new FontIcon(Feather.COPY)));
        revert.play();
    }

    @FXML
    private void onJoinRoom() {
        String userName = trimmed(userNameField.getText());
        String roomName = trimmed(roomNameField.getText());
        String serverUri = trimmed(serverField.getText());

        if (userName.isEmpty() || roomName.isEmpty()) {
            errorLabel.setText("Vui lòng nhập tên hiển thị và tên phòng.");
            return;
        }
        if (!demoModeCheckBox.isSelected() && serverUri.isEmpty()) {
            errorLabel.setText("Vui lòng nhập địa chỉ signaling server.");
            return;
        }

        try {
            Navigator.showRoom(stage, new RoomConfig(userName, roomName, serverUri, demoModeCheckBox.isSelected()));
        } catch (UnsupportedOperationException e) {
            errorLabel.setText(e.getMessage());
        }
    }

    private void refreshAvatar(String name) {
        avatarHolder.getChildren().setAll(Avatars.create(name, 24));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
