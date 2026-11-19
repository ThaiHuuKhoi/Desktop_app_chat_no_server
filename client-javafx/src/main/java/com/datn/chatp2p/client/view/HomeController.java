package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessions;
import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.common.signal.JoinRejectReason;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.common.log.SecretLogging;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sanh cho (man hinh Home) - De-cuong-Chat-P2P-Java.md muc 5 "tao/tham gia phong chat".
 *
 * <ul>
 *   <li><b>Vao phong</b>: bam 1 phong trong danh sach -&gt; hop xac nhan -&gt; neu phong co mat khau
 *       thi hop nhap mat khau -&gt; vao.</li>
 *   <li><b>Tao phong</b>: bam "Tao phong" -&gt; hop "Thong tin phong" (ten, tuy chon dat mat khau).</li>
 *   <li><b>Vao phong bang ten</b>: cho khach (khong co danh sach phong) hoac phong ban be gui ten.</li>
 * </ul>
 * Quy tac kiem tra / cau chu nam o {@link LobbyRules}, hop thoai o {@link RoomDialogs}.
 */
public class HomeController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOG = Logger.getLogger(HomeController.class.getName());
    private static final String DEFAULT_SERVER_URI = "ws://165.101.47.83:8080";

    @FXML
    private StackPane avatarHolder;

    @FXML
    private TextField userNameField;

    @FXML
    private VBox createColumn;

    @FXML
    private Button createRoomButton;

    @FXML
    private Button joinByNameButton;

    @FXML
    private Label joiningLabel;

    @FXML
    private TextField serverField;

    @FXML
    private VBox roomBrowserBox;

    @FXML
    private ListView<RoomSummary> roomListView;

    @FXML
    private TextField roomSearchField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button themeToggleButton;

    private String authToken;
    private List<RoomSummary> activeRooms = List.of();
    private Stage stage;
    private boolean accountMode;
    private boolean joining;

    @FXML
    private void initialize() {
        userNameField.textProperty().addListener((obs, old, name) -> refreshAvatar(name));
        userNameField.setText("Khách-" + (1000 + RANDOM.nextInt(9000)));
        serverField.setText(DEFAULT_SERVER_URI);
        roomSearchField.textProperty().addListener((obs, old, value) -> filterRooms(value));

        roomListView.setCellFactory(list -> new RoomCell());
        roomListView.setPlaceholder(new Label("Chưa có phòng nào đang hoạt động"));
        roomListView.setOnKeyPressed(event -> {
            RoomSummary selected = roomListView.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) {
                onRoomChosen(selected);
            }
        });

        ThemeManager.installToggle(themeToggleButton);
        // Chi chiem cho trong the khi thuc su co noi dung de hien
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.visibleProperty().bind(errorLabel.managedProperty());
        joiningLabel.managedProperty().bind(joiningLabel.textProperty().isNotEmpty());
        joiningLabel.visibleProperty().bind(joiningLabel.managedProperty());
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
        if (createColumn != null) {
            // Che do khach khong co cot danh sach phong -> cot tao phong gian het chieu ngang.
            createColumn.setMaxWidth(accountMode ? 300 : Double.MAX_VALUE);
            HBox.setHgrow(createColumn, accountMode ? Priority.NEVER : Priority.ALWAYS);
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

    // ------------------------------------------------------------ hanh dong

    /** Nut "Tao phong" -&gt; hop "Thong tin phong". */
    @FXML
    private void onCreateRoom() {
        if (!checkCanJoin()) {
            return;
        }
        RoomDialogs.showCreate(stage, activeRooms).ifPresent(choice -> {
            LOG.info(() -> "Tao phong '" + choice.roomName() + "' "
                    + (choice.password().isEmpty() ? "(cong khai)" : "(co mat khau: " + SecretLogging.reveal(choice.password()) + ")"));
            enterRoom(choice.roomName(), choice.password());
        });
    }

    /** Nut "Vao phong bang ten" -&gt; hop nhap ten + mat khau (neu co). */
    @FXML
    private void onJoinByName() {
        if (!checkCanJoin()) {
            return;
        }
        RoomDialogs.showJoinByName(stage).ifPresent(choice -> enterRoom(choice.roomName(), choice.password()));
    }

    /** Bam 1 phong trong danh sach -&gt; xac nhan -&gt; (neu co mat khau) nhap mat khau -&gt; vao. */
    private void onRoomChosen(RoomSummary room) {
        if (joining || !checkCanJoin()) {
            return;
        }
        if (!RoomDialogs.confirmJoin(stage, room)) {
            return;
        }
        if (!room.passwordProtected()) {
            enterRoom(room.roomId(), "");
            return;
        }
        RoomDialogs.askPassword(stage, room.roomId()).ifPresent(password -> enterRoom(room.roomId(), password));
    }

    @FXML
    private void onRefreshRooms() {
        loadRooms();
    }

    // ------------------------------------------------------------ vao phong

    /** Kiem tra chung truoc khi mo bat ky hop vao phong nao. */
    private boolean checkCanJoin() {
        errorLabel.setText("");
        String error = LobbyRules.validateUserName(LobbyRules.normalizeName(userNameField.getText()));
        if (error == null && LobbyRules.normalizeName(serverField.getText()).isEmpty()) {
            error = "Vui lòng nhập địa chỉ signaling server (mục Tuỳ chọn kết nối).";
        }
        if (error == null && accountMode && (authToken == null || authToken.isBlank())) {
            error = "Vui lòng đăng nhập trước khi vào phòng.";
        }
        if (error != null) {
            errorLabel.setText(error);
            return false;
        }
        return true;
    }

    /** Tao session tren luong nen (phong co mat khau can PBKDF2 ~0,1-0,3 s) roi mo man hinh phong. */
    private void enterRoom(String roomName, String roomPassword) {
        String userName = LobbyRules.normalizeName(userNameField.getText());
        String serverUri = LobbyRules.normalizeName(serverField.getText());
        LOG.info(() -> "Vao phong: ten=" + userName + ", phong=" + roomName
                + (roomPassword.isEmpty() ? " (cong khai)" : " (co mat khau: " + SecretLogging.reveal(roomPassword) + ")")
                + ", che do=" + (accountMode ? "tai khoan" : "khach"));
        RoomConfig config = new RoomConfig(userName, roomName, serverUri, accountMode ? authToken : "", roomPassword);
        setJoining(true, roomName);
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

    /**
     * Server tu choi cho vao phong (goi ngay sau khi quay ve Home). Hien ly do; sai mat khau thi mo lai
     * hop nhap mat khau cua dung phong do de nguoi dung nhap lai.
     */
    void showJoinRejected(RoomConfig config, String reason) {
        String message = LobbyRules.joinRejectedMessage(reason, config.roomName());
        errorLabel.setText(message);
        if (JoinRejectReason.WRONG_PASSWORD.equals(reason)) {
            Platform.runLater(() -> RoomDialogs.askPassword(stage, config.roomName(),
                            "Sai mật khẩu. Vui lòng nhập lại (phân biệt hoa thường).")
                    .ifPresent(password -> {
                        if (checkCanJoin()) {
                            enterRoom(config.roomName(), password);
                        }
                    }));
        }
    }

    private void joinFailed(String roomName, Throwable e) {
        LOG.log(Level.WARNING, "Khong vao duoc phong " + roomName, e);
        setJoining(false, null);
        Throwable cause = e instanceof LinkageError && e.getCause() != null ? e.getCause() : e;
        errorLabel.setText(cause.getMessage() == null ? "Không thể vào phòng." : cause.getMessage());
    }

    private void setJoining(boolean joining, String roomName) {
        this.joining = joining;
        createRoomButton.setDisable(joining);
        joinByNameButton.setDisable(joining);
        roomListView.setDisable(joining);
        joiningLabel.setText(joining ? "Đang vào phòng \"" + roomName + "\"…" : "");
    }

    // ------------------------------------------------------------ danh sach phong

    private void loadRooms() {
        if (authToken == null || authToken.isBlank()) {
            return;
        }
        try {
            activeRooms = new AuthClient().rooms(serverField.getText(), authToken);
            filterRooms(roomSearchField.getText());
        } catch (RuntimeException error) {
            errorLabel.setText(error.getMessage());
        }
    }

    private void filterRooms(String query) {
        if (roomListView == null) {
            return;
        }
        String normalized = LobbyRules.normalizeName(query).toLowerCase(Locale.ROOT);
        roomListView.getItems().setAll(activeRooms.stream()
                .filter(room -> room.roomId().toLowerCase(Locale.ROOT).contains(normalized))
                .toList());
    }

    private void refreshAvatar(String name) {
        avatarHolder.getChildren().setAll(Avatars.create(name, 24));
    }

    /** 1 dong trong danh sach phong: icon (khoa / nguoi), ten, so nguoi. Bam vao la bat dau vao phong. */
    private final class RoomCell extends ListCell<RoomSummary> {
        RoomCell() {
            setPrefWidth(0); // co theo chieu rong ListView, khong hien thanh cuon ngang
            setOnMouseClicked(event -> {
                if (!isEmpty() && getItem() != null) {
                    onRoomChosen(getItem());
                }
            });
        }

        @Override
        protected void updateItem(RoomSummary room, boolean empty) {
            super.updateItem(room, empty);
            setText(null);
            if (empty || room == null) {
                setGraphic(null);
                return;
            }
            FontIcon icon = new FontIcon(room.passwordProtected() ? Feather.LOCK : Feather.HASH);
            icon.getStyleClass().add(room.passwordProtected() ? "room-lock-icon" : "room-public-icon");
            Label name = new Label(room.roomId());
            name.getStyleClass().add("room-list-name");
            Label count = new Label(room.peerCount() + " người", new FontIcon(Feather.USERS));
            count.getStyleClass().add("room-list-count");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(8, icon, name, spacer, count);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
