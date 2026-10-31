package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.PeerIdentityService;
import com.datn.chatp2p.client.util.RoomNameGenerator;
import com.datn.chatp2p.client.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private TextField accountField;

    @FXML
    private javafx.scene.control.PasswordField passwordField;

    @FXML
    private VBox accountBox;

    @FXML
    private VBox roomBrowserBox;

    @FXML
    private ListView<AuthClient.RoomSummary> roomListView;

    @FXML
    private TextField roomSearchField;

    @FXML
    private Label errorLabel;

    private final AuthClient authClient = new AuthClient();
    private final PeerIdentityService identityService = new PeerIdentityService();
    private String authToken;
    private java.util.List<AuthClient.RoomSummary> activeRooms = java.util.List.of();

    @FXML
    private Button themeToggleButton;

    private Stage stage;
    private boolean accountMode;

    @FXML
    private void initialize() {
        userNameField.textProperty().addListener((obs, old, name) -> refreshAvatar(name));
        userNameField.setText("Khách-" + (1000 + RANDOM.nextInt(9000)));
        roomNameField.setText(RoomNameGenerator.generate());
        serverField.setText(DEFAULT_SERVER_URI);
        roomSearchField.textProperty().addListener((obs, old, value) -> filterRooms(value));

        ThemeManager.installToggle(themeToggleButton);
        // Chi chiem cho trong the khi thuc su co loi de hien
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.visibleProperty().bind(errorLabel.managedProperty());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setAccountMode(boolean accountMode) {
        this.accountMode = accountMode;
        if (accountBox != null) {
            accountBox.setVisible(accountMode);
            accountBox.setManaged(accountMode);
        }
        if (roomBrowserBox != null) {
            roomBrowserBox.setVisible(accountMode);
            roomBrowserBox.setManaged(accountMode);
        }
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
        if (serverUri.isEmpty()) {
            errorLabel.setText("Vui lòng nhập địa chỉ signaling server.");
            return;
        }
        if (accountMode && (authToken == null || authToken.isBlank())) {
            errorLabel.setText("Vui lòng đăng nhập trước khi vào phòng.");
            return;
        }

        try {
            Navigator.showRoom(stage, new RoomConfig(userName, roomName, serverUri,
                    accountMode ? authToken : ""));
        } catch (RuntimeException e) {
            errorLabel.setText(e.getMessage() == null ? "Không thể vào phòng." : e.getMessage());
        }
    }

    @FXML
    private void onLogin() {
            try {
                AuthClient.AuthResult result = authClient.login(serverField.getText(),
                        trimmed(accountField.getText()), passwordField.getText(), identityService.publicKeyBase64());
                authToken = result.token();
                userNameField.setText(result.username());
                errorLabel.setText("Đăng nhập thành công.");
                loadRooms();
            } catch (RuntimeException error) {
                errorLabel.setText(error.getMessage());
            }

            @FXML
            private void onRefreshRooms() {
                loadRooms();
            }

            @FXML
            private void onCreateRoom() {
                String roomName = trimmed(roomNameField.getText());
                if (roomName.isEmpty()) {
                    errorLabel.setText("Nhập tên phòng trước khi tạo.");
                    return;
                }
                confirmAndJoin(roomName);
            }

            @FXML
            private void onSelectRoom() {
                AuthClient.RoomSummary selected = roomListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    confirmAndJoin(selected.roomId());
                }
            }

            private void loadRooms() {
                if (authToken == null || authToken.isBlank()) return;
                try {
                    activeRooms = authClient.rooms(serverField.getText(), authToken);
                    filterRooms(roomSearchField.getText());
                } catch (RuntimeException error) {
                    errorLabel.setText(error.getMessage());
                }

                private void filterRooms(String query) {
                    if (roomListView == null) return;
                    String normalized = trimmed(query).toLowerCase(java.util.Locale.ROOT);
                    roomListView.getItems().setAll(activeRooms.stream()
                            .filter(room -> room.roomId().toLowerCase(java.util.Locale.ROOT).contains(normalized))
                            .toList());
                }
            }

            private void confirmAndJoin(String roomName) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Bạn có muốn vào phòng \"" + roomName + "\" không?",
                        ButtonType.CANCEL, ButtonType.OK);
                alert.setTitle("Xác nhận vào phòng");
                alert.setHeaderText("Tham gia phòng");
                if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    roomNameField.setText(roomName);
                    onJoinRoom();
                }
            }
        }

    @FXML
    private void onRegister() {
            try {
                authClient.register(serverField.getText(), trimmed(accountField.getText()),
                        passwordField.getText(), identityService.publicKeyBase64());
                errorLabel.setText("Đăng ký thành công. Hãy đăng nhập.");
            } catch (RuntimeException error) {
                errorLabel.setText(error.getMessage());
        }
    }

    private void refreshAvatar(String name) {
        avatarHolder.getChildren().setAll(Avatars.create(name, 24));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
