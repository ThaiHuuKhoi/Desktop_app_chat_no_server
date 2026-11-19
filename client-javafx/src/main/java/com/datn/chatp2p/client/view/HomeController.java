package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessions;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import com.datn.chatp2p.client.util.RoomNameGenerator;
import com.datn.chatp2p.client.util.ThemeManager;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(HomeController.class.getName());
    private static final String DEFAULT_SERVER_URI = "ws://165.101.47.83:8080";

    @FXML
    private StackPane avatarHolder;

    @FXML
    private TextField userNameField;

    @FXML
    private TextField roomNameField;

    @FXML
    private PasswordField roomPasswordField;

    @FXML
    private Button copyRoomButton;

    @FXML
    private Label roomHintLabel;

    @FXML
    private Button joinButton;

    @FXML
    private TextField serverField;

    @FXML
    private VBox roomBrowserBox;

    @FXML
    private ListView<AuthClient.RoomSummary> roomListView;

    @FXML
    private TextField roomSearchField;

    @FXML
    private Label errorLabel;

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
        if (roomBrowserBox != null) {
            roomBrowserBox.setVisible(accountMode);
            roomBrowserBox.setManaged(accountMode);
        }
    }

    public void setAuthenticatedUser(String username, String authToken) {
        this.authToken = authToken;
        setUserName(username);
        loadRooms();
    }

    /** Dien lai ten hien thi (vd. khach quay ve Home sau khi roi phong); bo qua neu rong. */
    public void setUserName(String userName) {
        if (userName != null && !userName.isBlank()) {
            userNameField.setText(userName);
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

        if (!LobbyRules.normalizePassword(roomPasswordField.getText()).isEmpty()) {
            // Phong co mat khau: nhac gui kem mat khau, roi tra lai dong goi y cu sau vai giay.
            String original = roomHintLabel.getText();
            roomHintLabel.setText("Đã sao chép tên phòng — nhớ gửi kèm mật khẩu cho bạn bè qua một kênh khác.");
            PauseTransition restore = new PauseTransition(Duration.seconds(5));
            restore.setOnFinished(e -> roomHintLabel.setText(original));
            restore.play();
        }
    }

    /** Nut chinh "Tao / Vao phong": dung ten + mat khau dang nhap. */
    @FXML
    private void onJoinRoom() {
        startJoin(trimmed(roomNameField.getText()), LobbyRules.normalizePassword(roomPasswordField.getText()));
    }

    @FXML
    private void onRefreshRooms() {
        loadRooms();
    }

    /**
     * Nut "Tao phong" o cot danh sach: cung luong voi nut chinh (server tu tao phong khi co nguoi
     * dau tien vao) - dung ten + mat khau dang nhap ben trai.
     */
    @FXML
    private void onCreateRoom() {
        onJoinRoom();
    }

    /**
     * Chon 1 phong trong danh sach: phong trong danh sach LUON la phong cong khai (phong co mat khau
     * khong duoc liet ke), nen bo mat khau dang nhap - neu giu, nguoi dung se roi vao 1 phong co mat
     * khau KHAC va thay phong trong.
     */
    @FXML
    private void onSelectRoom() {
        AuthClient.RoomSummary selected =
                roomListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!LobbyRules.normalizePassword(roomPasswordField.getText()).isEmpty()) {
            LOG.info("Chon phong cong khai tu danh sach - bo mat khau dang nhap");
            roomPasswordField.clear();
        }
        roomNameField.setText(selected.roomId());
        startJoin(selected.roomId(), "");
    }

    /** Luong vao phong DUY NHAT cho moi nut: kiem tra -> xac nhan -> tao session tren luong nen -> mo phong. */
    private void startJoin(String roomName, String roomPassword) {
        errorLabel.setText("");
        String userName = trimmed(userNameField.getText());
        String serverUri = trimmed(serverField.getText());

        String error = LobbyRules.validate(userName, roomName, roomPassword);
        if (error == null && serverUri.isEmpty()) {
            error = "Vui lòng nhập địa chỉ signaling server.";
        }
        if (error == null && accountMode && (authToken == null || authToken.isBlank())) {
            error = "Vui lòng đăng nhập trước khi vào phòng.";
        }
        if (error != null) {
            errorLabel.setText(error);
            return;
        }
        if (!confirmJoin(roomName, roomPassword, roomPassword.isEmpty() ? publicRoomPeerCount(roomName) : null)) {
            return;
        }

        LOG.info(() -> "Vao phong: ten=" + userName + ", phong=" + roomName
                + (roomPassword.isEmpty() ? " (cong khai)"
                        : " (co mat khau: " + com.datn.chatp2p.common.log.SecretLogging.reveal(roomPassword) + ")")
                + ", che do=" + (accountMode ? "tai khoan" : "khach"));
        RoomConfig config = new RoomConfig(userName, roomName, serverUri, accountMode ? authToken : "", roomPassword);
        setJoining(true);
        // Tao session tren luong nen: phong co mat khau can PBKDF2 (~0,1-0,3 s) - khong lam dung giao dien.
        Thread worker = new Thread(() -> {
            try {
                ChatSession session = ChatSessions.create(config);
                Platform.runLater(() -> {
                    try {
                        Navigator.showRoom(stage, config, session);
                    } catch (RuntimeException e) {
                        session.leave();
                        joinFailed(roomName, e);
                    }
                });
            } catch (RuntimeException | LinkageError e) {
                // LinkageError: vd cau hinh TURN sai lam P2pChatSession loi khi khoi tao lop.
                Platform.runLater(() -> joinFailed(roomName, e));
            }
        }, "join-room");
        worker.setDaemon(true);
        worker.start();
    }

    private void joinFailed(String roomName, Throwable e) {
        LOG.log(java.util.logging.Level.WARNING, "Khong vao duoc phong " + roomName, e);
        setJoining(false);
        Throwable cause = e instanceof LinkageError && e.getCause() != null ? e.getCause() : e;
        errorLabel.setText(cause.getMessage() == null ? "Không thể vào phòng." : cause.getMessage());
    }

    private void setJoining(boolean joining) {
        joinButton.setDisable(joining);
        joinButton.setText(joining ? "Đang vào phòng…" : "Tạo / Vào phòng");
    }

    /** So nguoi trong phong CONG KHAI cung ten theo danh sach da tai (null neu khong biet - vd che do khach). */
    private Integer publicRoomPeerCount(String roomName) {
        if (!accountMode) {
            return null;
        }
        return activeRooms.stream()
                .filter(room -> room.roomId().equals(roomName))
                .map(AuthClient.RoomSummary::peerCount)
                .findFirst()
                .orElse(0);
    }

    private boolean confirmJoin(String roomName, String roomPassword, Integer peersInPublicRoom) {
        ButtonType join = new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                LobbyRules.confirmText(roomName, roomPassword, peersInPublicRoom), join, cancel);
        alert.initOwner(stage);
        alert.setTitle("Xác nhận vào phòng");
        alert.setHeaderText(roomPassword.isEmpty() ? "Phòng công khai" : "Phòng có mật khẩu");
        Navigator.style(alert.getDialogPane());
        return alert.showAndWait().orElse(cancel) == join;
    }

    private void loadRooms() {
        if (authToken == null || authToken.isBlank()) {
            return;
        }
        try {
            AuthClient authClient = new AuthClient();
            activeRooms = authClient.rooms(serverField.getText(), authToken);
            filterRooms(roomSearchField.getText());
        } catch (RuntimeException error) {
            errorLabel.setText(error.getMessage());
        }
    }

    private void filterRooms(String query) {
        if (roomListView == null) {
            return;
        }
        String normalized = trimmed(query).toLowerCase(java.util.Locale.ROOT);
        roomListView.getItems().setAll(activeRooms.stream()
                // Phong co mat khau co roomId "ten~the" tren server - khong liet ke.
                .filter(room -> room.roomId().indexOf(RoomConfig.PRIVATE_ROOM_SEPARATOR) < 0)
                .filter(room -> room.roomId().toLowerCase(java.util.Locale.ROOT).contains(normalized))
                .toList());
    }

    private void refreshAvatar(String name) {
        avatarHolder.getChildren().setAll(Avatars.create(name, 24));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
