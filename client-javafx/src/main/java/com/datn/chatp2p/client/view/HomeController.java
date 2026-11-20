package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessions;
import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.common.log.SecretLogging;
import com.datn.chatp2p.common.signal.JoinRejectReason;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
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

import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sanh cho (man hinh Home) - De-cuong-Chat-P2P-Java.md muc 5 "tao/tham gia phong chat", bo cuc kieu
 * thanh ben giong cot trai cua man hinh phong.
 *
 * <ul>
 *   <li><b>Vao phong</b>: bam 1 phong trong danh sach -&gt; khung giua xem truoc phong (so nguoi, nguoi
 *       tao, co mat khau khong) -&gt; nhap mat khau ngay tai cho neu can -&gt; "Vào phòng".</li>
 *   <li><b>Vao phong bang ten</b>: go ten o o tim kiem roi Enter (phong co mat khau khong hien trong
 *       danh sach cua nguoi khac, va khach khong co danh sach), hoac nut "Vào phòng bằng tên".</li>
 *   <li><b>Tao phong</b>: hop "Thong tin phong" (ten, tuy chon dat mat khau).</li>
 * </ul>
 * Quy tac kiem tra / cau chu nam o {@link LobbyRules}, hop thoai o {@link RoomDialogs}.
 */
public class HomeController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOG = Logger.getLogger(HomeController.class.getName());
    private static final String DEFAULT_SERVER_URI = "wss://chat.zingme.fun";

    @FXML private StackPane avatarHolder;
    @FXML private Label selfNameLabel;
    @FXML private Label serverLabel;
    @FXML private Label modeLabel;
    @FXML private Button loginButton;
    @FXML private Button backToLoginButton;
    @FXML private TextField userNameField;
    @FXML private VBox nameBox;
    @FXML private Button createRoomButton;
    @FXML private Button refreshButton;
    @FXML private Label roomSectionLabel;
    @FXML private Label sidebarHint;
    @FXML private Label joiningLabel;
    @FXML private TextField serverField;
    @FXML private ListView<RoomSummary> roomListView;
    @FXML private TextField roomSearchField;
    @FXML private Label errorLabel;
    @FXML private Button themeToggleButton;

    @FXML private VBox welcomePane;
    @FXML private Label welcomeTitle;
    @FXML private VBox previewPane;
    @FXML private FontIcon previewIcon;
    @FXML private Label previewName;
    @FXML private Label previewMeta;
    @FXML private VBox previewPasswordBox;
    @FXML private Label previewPasswordLabel;
    @FXML private PasswordField previewPasswordField;
    @FXML private Button joinButton;
    @FXML private Label previewHint;

    private String authToken;
    private List<RoomSummary> activeRooms = List.of();
    private Stage stage;
    private boolean accountMode;
    private boolean joining;
    /** Phong dang xem truoc: ten, va thong tin tu danh sach (null = nhap theo ten, khong biet co mat khau khong). */
    private String previewRoomName;
    private RoomSummary previewRoom;

    @FXML
    private void initialize() {
        userNameField.textProperty().addListener((obs, old, name) -> refreshIdentity());
        userNameField.setText("Khách-" + (1000 + RANDOM.nextInt(9000)));
        serverField.textProperty().addListener((obs, old, uri) -> refreshIdentity());
        serverField.setText(DEFAULT_SERVER_URI);

        roomSearchField.textProperty().addListener((obs, old, value) -> filterRooms(value));
        roomSearchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onSearchEnter();
            }
        });

        roomListView.setCellFactory(list -> new RoomCell());
        roomListView.getSelectionModel().selectedItemProperty().addListener((obs, old, room) -> {
            if (room != null && !joining) {
                showPreview(room.roomId(), room);
            }
        });

        ThemeManager.installToggle(themeToggleButton);
        // Chi chiem cho khi thuc su co noi dung de hien
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());
        errorLabel.visibleProperty().bind(errorLabel.managedProperty());
        joiningLabel.managedProperty().bind(joiningLabel.textProperty().isNotEmpty());
        joiningLabel.visibleProperty().bind(joiningLabel.managedProperty());
        previewPasswordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onJoinPreview();
            }
        });
        setAccountMode(false);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setAccountMode(boolean accountMode) {
        this.accountMode = accountMode;
        // Tai khoan: ten = ten dang nhap (server cung ep), khong cho sua. Khach: tu dat ten.
        nameBox.setVisible(!accountMode);
        nameBox.setManaged(!accountMode);
        refreshButton.setVisible(accountMode);
        modeLabel.setText(accountMode ? "Tài khoản" : "Khách");
        // Khach: nut quay lai dang nhap (o the cua minh thay cho nhan "Khach", va dau loi chao)
        modeLabel.setVisible(accountMode);
        modeLabel.setManaged(accountMode);
        loginButton.setVisible(!accountMode);
        loginButton.setManaged(!accountMode);
        backToLoginButton.setVisible(!accountMode);
        backToLoginButton.setManaged(!accountMode);
        roomListView.setPlaceholder(placeholder(accountMode
                ? "Chưa có phòng nào đang hoạt động.\nTạo phòng mới hoặc nhập tên phòng ở ô trên."
                : "Khách không xem được danh sách phòng.\nGõ tên phòng bạn bè đã gửi ở ô trên rồi nhấn Enter."));
        sidebarHint.setVisible(accountMode);
        sidebarHint.setManaged(accountMode);
        refreshIdentity();
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

    /** Nut "Tao phong moi" -&gt; hop "Thong tin phong". */
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

    /** Enter o o tim kiem: khop dung 1 phong trong danh sach -&gt; xem truoc phong do, khong thi vao theo ten. */
    private void onSearchEnter() {
        String name = LobbyRules.normalizeName(roomSearchField.getText());
        String error = LobbyRules.validateRoomName(name);
        if (error != null) {
            errorLabel.setText(error);
            return;
        }
        errorLabel.setText("");
        RoomSummary listed = findRoom(name);
        showPreview(name, listed);
    }

    /** Khach chon nham "Vào với tư cách khách" -&gt; quay lai man hinh dang nhap. */
    @FXML
    private void onBackToLogin() {
        if (!joining) {
            Navigator.showAuth(stage);
        }
    }

    @FXML
    private void onRefreshRooms() {
        loadRooms();
    }

    @FXML
    private void onBackToWelcome() {
        previewRoomName = null;
        previewRoom = null;
        roomListView.getSelectionModel().clearSelection();
        errorLabel.setText("");
        welcomePane.setVisible(true);
        welcomePane.setManaged(true);
        previewPane.setVisible(false);
        previewPane.setManaged(false);
    }

    /** Nut "Vao phong" trong khung xem truoc. */
    @FXML
    private void onJoinPreview() {
        if (previewRoomName == null || joining || !checkCanJoin()) {
            return;
        }
        String password = LobbyRules.normalizePassword(previewPasswordField.getText());
        if (previewRoom != null && previewRoom.passwordProtected()) {
            String error = LobbyRules.validateJoinPassword(password);
            if (error != null) {
                errorLabel.setText(error);
                previewPasswordField.requestFocus();
                return;
            }
        }
        enterRoom(previewRoomName, previewRoom != null && !previewRoom.passwordProtected() ? "" : password);
    }

    // ------------------------------------------------------------ xem truoc phong

    /**
     * Khung giua hien phong {@code roomName}. {@code room} = thong tin tu danh sach, hoac null khi nhap
     * theo ten (khong biet phong co mat khau khong -&gt; o mat khau tuy chon).
     */
    private void showPreview(String roomName, RoomSummary room) {
        previewRoomName = roomName;
        previewRoom = room;
        errorLabel.setText("");
        previewName.setText(roomName);
        boolean locked = room == null || room.passwordProtected();
        previewIcon.setIconLiteral(room == null ? "fth-log-in" : locked ? "fth-lock" : "fth-hash");
        previewMeta.setText(room == null ? "Vào phòng theo tên" : LobbyRules.previewMeta(room));
        previewPasswordBox.setVisible(locked);
        previewPasswordBox.setManaged(locked);
        previewPasswordLabel.setText(room == null ? "Mật khẩu phòng (nếu có)" : "Mật khẩu phòng");
        previewPasswordField.clear();
        previewPasswordField.setPromptText(room == null
                ? "Để trống nếu là phòng công khai"
                : "Nhập mật khẩu" + (room.ownerUserName() == null ? "" : " " + room.ownerUserName()) + " đã gửi cho bạn");
        boolean full = room != null && LobbyRules.isFull(room);
        joinButton.setDisable(full || joining);
        joinButton.setText(full ? "Phòng đã đầy" : "Vào phòng");
        previewHint.setText(LobbyRules.previewHint(room));

        welcomePane.setVisible(false);
        welcomePane.setManaged(false);
        previewPane.setVisible(true);
        previewPane.setManaged(true);
        Platform.runLater(() -> {
            if (locked) {
                previewPasswordField.requestFocus();
            } else {
                joinButton.requestFocus();
            }
        });
    }

    private RoomSummary findRoom(String name) {
        return activeRooms.stream().filter(room -> room.roomId().equals(name)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------ vao phong

    /** Kiem tra chung truoc khi vao phong. */
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
     * khung xem truoc cua dung phong do de nguoi dung nhap lai mat khau ngay tai cho.
     */
    void showJoinRejected(RoomConfig config, String reason) {
        String message = LobbyRules.joinRejectedMessage(reason, config.roomName());
        if (JoinRejectReason.WRONG_PASSWORD.equals(reason)) {
            showPreview(config.roomName(), findRoom(config.roomName()));
        }
        errorLabel.setText(message);
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
        joinButton.setDisable(joining || (previewRoom != null && LobbyRules.isFull(previewRoom)));
        roomListView.setDisable(joining);
        roomSearchField.setDisable(joining);
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
        roomSectionLabel.setText("ĐANG HOẠT ĐỘNG · " + activeRooms.size());
    }

    /** Ten + avatar + server o the cua minh, loi chao o khung giua. */
    private void refreshIdentity() {
        String name = LobbyRules.normalizeName(userNameField.getText());
        avatarHolder.getChildren().setAll(Avatars.create(name, 18));
        selfNameLabel.setText(name.isEmpty() ? "Chưa đặt tên" : name);
        welcomeTitle.setText(name.isEmpty() ? "Chào bạn" : "Chào " + name);
        serverLabel.setText(hostOf(serverField.getText()));
    }

    private static String hostOf(String uri) {
        try {
            String host = URI.create(LobbyRules.normalizeName(uri)).getHost();
            return host == null ? uri : host;
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }

    private static Label placeholder(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("lobby-placeholder");
        return label;
    }

    /** 1 dong trong danh sach phong: icon (khoa / #), ten, so nguoi hoac "Đầy". Chon -&gt; xem truoc. */
    private static final class RoomCell extends ListCell<RoomSummary> {
        RoomCell() {
            setPrefWidth(0); // co theo chieu rong ListView, khong hien thanh cuon ngang
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
            name.setMinWidth(0);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            boolean full = LobbyRules.isFull(room);
            Label count = new Label(full ? "Đầy" : room.peerCount() + "/" + LobbyRules.MAX_PEERS_PER_ROOM);
            count.getStyleClass().add(full ? "room-list-full" : "room-list-count");
            HBox row = new HBox(10, icon, name, spacer, count);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
