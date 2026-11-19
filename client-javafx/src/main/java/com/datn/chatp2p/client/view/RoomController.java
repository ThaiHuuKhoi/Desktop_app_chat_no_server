package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import com.datn.chatp2p.client.ChatApplication;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessionListener;
import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.session.SessionPeer;
import com.datn.chatp2p.client.session.SessionPeer.ConnectionKind;
import com.datn.chatp2p.client.util.DesktopNotifier;
import com.datn.chatp2p.client.util.SoundPlayer;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.client.util.TrustedPeers;
import com.datn.chatp2p.client.util.UserSettings;
import com.datn.chatp2p.client.view.TranscriptEntry.NoticeKind;
import com.datn.chatp2p.client.view.TranscriptEntry.SystemNotice;
import com.datn.chatp2p.client.view.TranscriptEntry.UserMessage;
import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.PeerVerificationState;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Man hinh phong chat (De-cuong-Chat-P2P-Java.md muc 5): cot trai la phong +
 * nguoi trong phong, ben phai la khung chat voi dong trang thai bao mat.
 *
 * <p>Chi noi chuyen voi tang mang qua {@link ChatSession} - khong goi truc
 * tiep ma hoa hay DataChannel. Khi tich hop {@code RoomSession} cua p2p-core,
 * lop nay giu nguyen.
 */
public class RoomController implements ChatSessionListener {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(RoomController.class.getName());

    @FXML
    private Label roomNameLabel;

    @FXML
    private Label roomSubtitleLabel;

    @FXML
    private Label roomDescriptionLabel;

    @FXML
    private Label peerSectionLabel;

    @FXML
    private ListView<PeerItem> peerListView;

    @FXML
    private TabPane chatTabs;

    @FXML
    private StackPane selfAvatarHolder;

    @FXML
    private Label selfNameLabel;

    @FXML
    private Button themeToggleButton;

    @FXML
    private HBox statusChips;

    @FXML
    private ListView<TranscriptEntry> transcriptListView;

    @FXML
    private VBox emptyState;

    @FXML
    private Label emptyStateRoomLabel;

    @FXML
    private TextArea messageArea;

    @FXML
    private Label typingLabel;

    private final ObservableList<PeerItem> peers = FXCollections.observableArrayList();
    private final TypingTracker typingTracker = new TypingTracker(System::currentTimeMillis);
    private TypingNotifier typingNotifier;
    /** Moi giay: het han "dang go" cua nguoi khac + bao "ngung go" cua minh khi dung tay. */
    private final Timeline typingTicker = new Timeline(new KeyFrame(Duration.seconds(1), event -> onTypingTick()));
    private static final int ALONE_HINT_SECONDS = 20;
    /** Da nhan JOIN_REJECTED va dang quay ve sanh (server co the gui lai khi tu ket noi lai). */
    private boolean joinRejected;
    /** Peer da duoc canh bao "khoa da doi" (chi canh bao 1 lan moi peer). */
    private final java.util.Set<String> keyChangeWarned = new java.util.HashSet<>();
    /** Trang thai "dang gui / da nhan" cua cac tin minh gui. */
    private final DeliveryStatuses deliveryStatuses = new DeliveryStatuses();
    private final javafx.animation.PauseTransition aloneHintTimer =
            new javafx.animation.PauseTransition(Duration.seconds(ALONE_HINT_SECONDS));
    private final Map<String, String> displayNames = new HashMap<>();
    private final Map<String, SessionPeer> pendingPeerUpdates = new HashMap<>();

    private Stage stage;
    private RoomConfig config;
    private ChatSession session;
    private ChatTranscriptManager transcriptManager;

    @FXML
    private void initialize() {
        ThemeManager.installToggle(themeToggleButton);
        // TextArea khong co onAction nhu TextField - tu bat phim: Enter gui,
        // Shift+Enter xuong dong (Tai-lieu-ky-thuat.md muc E.8.1).
        messageArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onMessageAreaKeyPressed);
    }

    public void start(Stage stage, RoomConfig config, ChatSession session) {
        this.stage = stage;
        this.config = config;
        this.session = session;

        displayNames.put(session.selfPeerId(), config.userName());
        roomNameLabel.setText(config.roomName());
        roomDescriptionLabel.setText(config.isPasswordProtected()
                ? "Phòng có mật khẩu · chỉ người nhập đúng mật khẩu mới thấy nhau"
                : "Phòng công khai · ai biết tên phòng đều vào được");
        emptyStateRoomLabel.setText(config.roomName());
        selfNameLabel.setText(config.userName());
        selfAvatarHolder.getChildren().setAll(Avatars.create(config.userName(), 16));

        peerListView.setItems(peers);
        peerListView.setCellFactory(list -> new PeerListCell(this::openDirectMessage, this::verifyFingerprint,
                item -> TrustedPeers.get().status(item.peer.displayName(), item.peer.remoteFingerprint())));
        peerListView.setPlaceholder(new Label("Chưa có ai khác"));

        transcriptManager = new ChatTranscriptManager(
                session.selfPeerId(),
                peerId -> displayNames.getOrDefault(peerId, "Người lạ"),
                new FileCardActions(),
                deliveryStatuses::status);
        transcriptManager.configure(chatTabs, transcriptListView);
        installFileDrop();

        typingNotifier = new TypingNotifier(session::sendTypingStatus, System::currentTimeMillis);
        messageArea.textProperty().addListener((obs, old, text) -> typingNotifier.onTextChanged(
                transcriptManager.selectedDirectPeerId(), text == null || text.isBlank()));
        chatTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            typingNotifier.stop(); // dang go o tab cu -> bao ngung truoc khi sang tab moi
            refreshTypingLabel();
        });
        typingTicker.setCycleCount(Animation.INDEFINITE);
        typingTicker.play();
        // Sai mat khau / sai ten phong trong y het "chua ai vao" -> sau 1 luc van mot minh thi goi y kiem tra lai.
        aloneHintTimer.setOnFinished(event -> {
            if (peers.isEmpty()) {
                LOG.info("Van mot minh trong phong sau " + ALONE_HINT_SECONDS + " giay - hien goi y kiem tra ten/mat khau");
                notice(NoticeKind.WARNING, LobbyRules.aloneHint(config.isPasswordProtected()));
            }
        });
        aloneHintTimer.play();
        DesktopNotifier.setOnClick(() -> Platform.runLater(() -> {
            stage.setIconified(false);
            stage.toFront();
            stage.requestFocus();
        }));

        // Dong cua so khi dang trong phong cung phai roi phong dang hoang (dong ket noi P2P/signaling)
        stage.setOnCloseRequest(event -> leaveSession());

        notice(NoticeKind.INFO, "Bạn đã vào phòng " + config.roomName()
                + ".");
        refreshRoomState();
        session.start(this);
        Platform.runLater(messageArea::requestFocus);
    }

    // ---------- Su kien tu ChatSession (co the tu thread mang) ----------

    @Override
    public void onPeerJoined(SessionPeer peer) {
        Platform.runLater(() -> {
            SessionPeer joinedPeer = pendingPeerUpdates.remove(peer.peerId());
            SessionPeer effectivePeer = joinedPeer == null ? peer : joinedPeer;
            displayNames.put(effectivePeer.peerId(), effectivePeer.displayName());
            peers.add(new PeerItem(effectivePeer));
            notice(NoticeKind.JOINED, effectivePeer.displayName() + " đã vào phòng");
            notice(NoticeKind.SECURE, "Đã thiết lập kênh mã hoá đầu-cuối với " + effectivePeer.displayName()
                    + ".");
            refreshRoomState();
        });
    }

    @Override
    public void onPeerIdentityUpdated(SessionPeer peer) {
        Platform.runLater(() -> {
            boolean updated = false;
            for (int i = 0; i < peers.size(); i++) {
                if (peers.get(i).peer.peerId().equals(peer.peerId())) {
                    peers.set(i, new PeerItem(peer));
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                pendingPeerUpdates.put(peer.peerId(), peer);
            }
            warnIfKeyChanged(peer);
            peerListView.refresh();
            refreshRoomState();
        });
    }

    /** Nguoi nay da tung duoc xac minh van tay ma gio van tay khac -&gt; canh bao ro (1 lan moi peer). */
    private void warnIfKeyChanged(SessionPeer peer) {
        if (peer.verificationState() != PeerVerificationState.VERIFIED || !keyChangeWarned.add(peer.peerId())) {
            return;
        }
        if (TrustedPeers.get().status(peer.displayName(), peer.remoteFingerprint()) == TrustedPeers.Status.CHANGED) {
            LOG.warning("Van tay cua '" + peer.displayName() + "' khac lan da xac minh truoc do");
            notice(NoticeKind.WARNING, "Khoá danh tính của " + peer.displayName()
                    + " KHÁC với lần bạn đã xác minh trước đây. Có thể là người khác dùng cùng tên, "
                    + "hoặc họ đã đổi máy. Đừng gửi thông tin nhạy cảm cho tới khi so lại vân tay "
                    + "(bấm vào biểu tượng cạnh tên họ).");
        }
    }

    /** Mo hop so van tay; "Khop" -&gt; ghi nho la tin cay, "Khong khop" -&gt; bo tin cay + canh bao. */
    private void verifyFingerprint(PeerItem item) {
        if (item.peer.remoteFingerprint() == null) {
            notice(NoticeKind.INFO, "Chưa có vân tay của " + item.peer.displayName() + ", vui lòng đợi xác minh xong.");
            return;
        }
        switch (FingerprintDialog.show(stage, item)) {
            case MATCH -> {
                TrustedPeers.get().trust(item.peer.displayName(), item.peer.remoteFingerprint());
                notice(NoticeKind.SECURE, "Đã đánh dấu tin cậy " + item.peer.displayName()
                        + ". Lần sau nếu khoá của người tên này thay đổi, bạn sẽ được cảnh báo.");
            }
            case MISMATCH -> {
                TrustedPeers.get().forget(item.peer.displayName());
                notice(NoticeKind.WARNING, "Vân tay của " + item.peer.displayName()
                        + " KHÔNG khớp: có thể có người đang xen giữa. Đừng gửi thông tin nhạy cảm.");
            }
            case CANCELLED -> {
                return;
            }
        }
        peerListView.refresh();
    }

    @Override
    public void onMessageSent(String messageId, List<String> recipientPeerIds) {
        Platform.runLater(() -> {
            deliveryStatuses.sent(messageId, recipientPeerIds);
            transcriptManager.refreshAll();
        });
    }

    @Override
    public void onMessageDelivered(String messageId, String peerId) {
        Platform.runLater(() -> {
            if (deliveryStatuses.delivered(messageId, peerId)) {
                transcriptManager.refreshAll();
            }
        });
    }

    @Override
    public void onMessageDeliveryFailed(String messageId, String peerId) {
        Platform.runLater(() -> {
            if (deliveryStatuses.failed(messageId, peerId)) {
                transcriptManager.refreshAll();
            }
        });
    }

    @Override
    public void onPeerLeft(String peerId) {
        Platform.runLater(() -> {
            if (deliveryStatuses.peerLeft(peerId)) {
                transcriptManager.refreshAll();
            }
            peers.removeIf(item -> item.peer.peerId().equals(peerId));
            pendingPeerUpdates.remove(peerId);
            typingTracker.removePeer(peerId);
            refreshTypingLabel();
            notice(NoticeKind.LEFT, displayNames.getOrDefault(peerId, "Một người") + " đã rời phòng");
            refreshRoomState();
        });
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        Platform.runLater(() -> {
            transcriptManager.addGroup(new UserMessage(message));
            typingTracker.clear(message.getAuthorId(), false);
            refreshTypingLabel();
            refreshRoomState();
            alertIncoming(message.getAuthorId(), false);
        });
    }

    @Override
    public void onDirectMessageReceived(String peerId, ChatMessage message) {
        Platform.runLater(() -> {
            // Dang go do 1 tin khac thi khong keo nguoi dung sang tab DM - chi them tin va bao.
            transcriptManager.addDirect(
                    peerId,
                    displayNames.getOrDefault(peerId, "Người lạ"),
                    message,
                    messageArea.getText().isBlank());
            typingTracker.clear(peerId, true);
            refreshTypingLabel();
            alertIncoming(peerId, true);
        });
    }

    @Override
    public void onHistoryReceived(String fromPeerId, List<ChatMessage> messages, Map<String, String> authorNames) {
        Platform.runLater(() -> {
            authorNames.forEach(displayNames::putIfAbsent);
            String sharedBy = displayNames.getOrDefault(fromPeerId, "Một người");
            int added = transcriptManager.mergeGroupHistory(messages, sharedBy);
            if (added > 0) {
                notice(NoticeKind.INFO, "Đã nhận " + added + " tin nhắn trước đó do " + sharedBy
                        + " chia sẻ (không xác minh được người viết từng tin)");
                refreshRoomState();
            }
        });
    }

    @Override
    public void onPeerTyping(String peerId, boolean direct, boolean typing) {
        Platform.runLater(() -> {
            typingTracker.update(peerId, direct, typing);
            refreshTypingLabel();
        });
    }

    @Override
    public void onFileTransferUpdated(FileTransferStatus status) {
        Platform.runLater(() -> {
            transcriptManager.upsertFile(status, displayNames.getOrDefault(status.peerId(), "Người lạ"));
            refreshRoomState();
        });
    }

    @Override
    public void onJoinRejected(String reason) {
        Platform.runLater(() -> {
            if (joinRejected) {
                return;
            }
            joinRejected = true;
            LOG.info("Server tu choi cho vao phong " + config.roomName() + ": " + reason + " - quay ve sanh");
            leaveSession();
            Navigator.showHomeAfterJoinRejected(stage, config, reason);
        });
    }

    @Override
    public void onPeerConnectionFailed(String peerId, String reason) {
        Platform.runLater(() -> notice(NoticeKind.WARNING,
                "Không kết nối được với " + displayNames.getOrDefault(peerId, "một người") + ": " + reason));
    }

    // ---------- Hanh dong cua nguoi dung ----------

    private void onMessageAreaKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) {
            return;
        }
        event.consume();
        if (event.isShiftDown()) {
            messageArea.replaceSelection("\n");
        } else {
            onSendMessage();
        }
    }

    @FXML
    private void onSendMessage() {
        String text = messageArea.getText() == null ? "" : messageArea.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        ChatMessage message = new ChatMessage(session.selfPeerId(), text);
        String directPeerId = transcriptManager.selectedDirectPeerId();
        if (directPeerId == null) {
            transcriptManager.addGroup(new UserMessage(message));
        }
        messageArea.clear();
        refreshRoomState();

        try {
            if (directPeerId == null) {
                session.sendGroupMessage(message);
            } else {
                session.sendDirectMessage(directPeerId, message);
                transcriptManager.addDirect(
                        directPeerId,
                        displayNames.getOrDefault(directPeerId, "Người lạ"),
                        message);
            }
        } catch (RuntimeException e) {
            // Khong im lang khi gui loi (bai hoc tu issue #618 cua chitchatter)
            notice(NoticeKind.WARNING, "Gửi tin thất bại: " + e.getMessage());
        }
    }

    // ---------- Gui file ----------

    @FXML
    private void onAttachFile() {
        if (fileTargets().isEmpty()) {
            notice(NoticeKind.WARNING, "Chưa có ai trong phòng để gửi tệp");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn tệp để gửi");
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            sendFile(file.toPath());
        }
    }

    /** Keo tha file vao khung chat de gui. */
    private void installFileDrop() {
        chatTabs.setOnDragOver(event -> {
            Dragboard board = event.getDragboard();
            if (board.hasFiles() && board.getFiles().size() == 1 && board.getFiles().get(0).isFile()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        chatTabs.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            boolean accepted = board.hasFiles() && board.getFiles().size() == 1;
            if (accepted) {
                sendFile(board.getFiles().get(0).toPath());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    /** Tab DM -> gui cho nguoi do; tab Nhom -> gui rieng cho tung nguoi trong phong (1-1). */
    private List<String> fileTargets() {
        String directPeerId = transcriptManager.selectedDirectPeerId();
        if (directPeerId != null) {
            boolean stillHere = peers.stream().anyMatch(item -> item.peer.peerId().equals(directPeerId));
            return stillHere ? List.of(directPeerId) : List.of();
        }
        return peers.stream().map(item -> item.peer.peerId()).toList();
    }

    private void sendFile(Path file) {
        List<String> targets = fileTargets();
        if (targets.isEmpty()) {
            notice(NoticeKind.WARNING, "Chưa có ai trong phòng để gửi tệp");
            return;
        }
        long maxSize = session.maxFileSize();
        long size = file.toFile().length();
        if (maxSize > 0 && size > maxSize) {
            notice(NoticeKind.WARNING, "Tệp " + file.getFileName() + " (" + FileTransferTexts.size(size)
                    + ") vượt giới hạn " + FileTransferTexts.size(maxSize));
            return;
        }
        boolean direct = transcriptManager.selectedDirectPeerId() != null;
        for (String peerId : targets) {
            // sendFile doc ca file de tinh SHA-256 -> chay ngoai luong giao dien
            CompletableFuture.runAsync(() -> session.sendFile(peerId, file, direct))
                    .exceptionally(error -> {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        Platform.runLater(() -> notice(NoticeKind.WARNING,
                                "Không gửi được tệp " + file.getFileName() + ": " + cause.getMessage()));
                        return null;
                    });
        }
    }

    /** Thu muc luu file nhan: ~/Downloads (tao neu chua co). */
    private static Path downloadDirectory() {
        return Path.of(System.getProperty("user.home"), "Downloads");
    }

    /** Xu ly cac nut tren the file. */
    private final class FileCardActions implements FileTransferActions {

        @Override
        public void accept(FileTransferStatus status) {
            runFileAction(() -> session.acceptFile(status.fileId(), downloadDirectory()));
        }

        @Override
        public void reject(FileTransferStatus status) {
            runFileAction(() -> session.rejectFile(status.fileId()));
        }

        @Override
        public void cancel(FileTransferStatus status) {
            runFileAction(() -> session.cancelFile(status.fileId()));
        }

        @Override
        public void showInFolder(FileTransferStatus status) {
            Path saved = status.savedFile();
            CompletableFuture.runAsync(() -> {
                try {
                    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                        // Mo Explorer va chon san file vua nhan
                        new ProcessBuilder("explorer.exe", "/select,", saved.toString()).start();
                    } else {
                        Desktop.getDesktop().open(saved.getParent().toFile());
                    }
                } catch (IOException | RuntimeException e) {
                    Platform.runLater(() -> notice(NoticeKind.WARNING,
                            "Không mở được thư mục: " + saved.getParent()));
                }
            });
        }

        private void runFileAction(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException e) {
                notice(NoticeKind.WARNING, "Thao tác với tệp thất bại: " + e.getMessage());
            }
        }
    }

    private void openDirectMessage(PeerItem item) {
        transcriptManager.openDirect(
                item.peer.peerId(),
                displayNames.getOrDefault(item.peer.peerId(), "Người lạ"));
    }

    @FXML
    private void onCopyRoomName() {
        ClipboardContent content = new ClipboardContent();
        content.putString(config.roomName());
        Clipboard.getSystemClipboard().setContent(content);
        notice(NoticeKind.INFO, config.isPasswordProtected()
                ? "Đã sao chép tên phòng — gửi kèm mật khẩu qua một kênh khác cho người bạn muốn chat cùng"
                : "Đã sao chép tên phòng — gửi cho người bạn muốn chat cùng");
    }

    @FXML
    private void onLeaveRoom() {
        ButtonType leave = new ButtonType("Rời phòng", ButtonBar.ButtonData.OK_DONE);
        ButtonType stay = new ButtonType("Ở lại", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Toàn bộ tin nhắn trong phòng sẽ bị xoá khỏi máy bạn và không thể khôi phục.", leave, stay);
        confirm.initOwner(stage);
        confirm.setTitle("Rời phòng");
        confirm.setHeaderText("Rời phòng " + config.roomName() + "?");
        confirm.getDialogPane().lookupButton(leave).getStyleClass().add(Styles.DANGER);
        Navigator.style(confirm.getDialogPane());

        if (confirm.showAndWait().orElse(stay) == leave) {
            leaveSession();
            Navigator.showHomeAfterLeaving(stage, config);
        }
    }

    @FXML
    private void onOpenSettings() {
        SettingsDialog.show(stage, () -> ThemeManager.installToggle(themeToggleButton));
    }

    private void leaveSession() {
        typingNotifier.stop();
        typingTicker.stop();
        aloneHintTimer.stop();
        DesktopNotifier.setOnClick(null);
        session.leave();
    }

    // ---------- Dang go / thong bao ----------

    private void onTypingTick() {
        if (typingNotifier != null) {
            typingNotifier.tick();
        }
        refreshTypingLabel();
    }

    private void refreshTypingLabel() {
        if (transcriptManager == null) {
            return;
        }
        String directPeerId = transcriptManager.selectedDirectPeerId();
        String text;
        if (directPeerId == null) {
            text = TypingTracker.describe(typingTracker.groupTypers().stream()
                    .map(id -> displayNames.getOrDefault(id, "Người lạ"))
                    .toList());
        } else {
            text = typingTracker.isTypingDirect(directPeerId)
                    ? TypingTracker.describe(List.of(displayNames.getOrDefault(directPeerId, "Người lạ")))
                    : "";
        }
        typingLabel.setText(text);
        typingLabel.setVisible(!text.isEmpty());
        typingLabel.setManaged(!text.isEmpty());
    }

    /**
     * Tin moi toi: am bao neu nguoi dung khong dang nhin dung khung chat do; thong bao man hinh neu
     * cua so khong duoc chon. Thong bao chi co ten nguoi gui (xem {@link DesktopNotifier}).
     */
    private void alertIncoming(String peerId, boolean direct) {
        UserSettings settings = UserSettings.get();
        boolean focused = stage.isFocused();
        String selectedDirect = transcriptManager.selectedDirectPeerId();
        boolean looking = focused && (direct ? peerId.equals(selectedDirect) : selectedDirect == null);
        LOG.fine(() -> "Tin moi tu " + displayNames.getOrDefault(peerId, peerId) + ": cua so "
                + (focused ? "dang chon" : "KHONG duoc chon") + ", dang xem dung khung chat=" + looking
                + " -> am bao=" + (!looking && settings.soundEnabled())
                + ", thong bao=" + (!focused && settings.desktopNotificationsEnabled()));
        if (!looking && settings.soundEnabled()) {
            SoundPlayer.playIncoming();
        }
        if (!focused && settings.desktopNotificationsEnabled()) {
            String name = displayNames.getOrDefault(peerId, "Một người");
            DesktopNotifier.notify(ChatApplication.APP_TITLE, direct
                    ? name + " đã nhắn riêng cho bạn"
                    : name + " đã gửi tin nhắn trong phòng " + config.roomName());
        }
    }

    // ---------- Cap nhat giao dien ----------

    private void notice(NoticeKind kind, String text) {
        transcriptManager.addGroup(new SystemNotice(kind, text));
    }

    private void refreshRoomState() {
        int total = peers.size() + 1;
        roomSubtitleLabel.setText(total + " người · mã hoá đầu-cuối");
        peerSectionLabel.setText("NGƯỜI KHÁC TRONG PHÒNG (" + peers.size() + ")");

        boolean hasUserMessages = transcriptManager.hasGroupUserMessages();
        emptyState.setVisible(peers.isEmpty() && !hasUserMessages);

        statusChips.getChildren().setAll(buildStatusChips());
    }

    /** Dong trang thai tren header: duong truyen, xac thuc, ma hoa - doi theo trang thai that. */
    private List<Label> buildStatusChips() {
        if (peers.isEmpty()) {
            return List.of(chip(Feather.CLOCK, "Đang chờ người khác vào phòng", "chip-neutral"));
        }

        Label connection;
        if (peers.stream().anyMatch(p -> p.peer.connectionKind() == ConnectionKind.RELAY)) {
            connection = chip(Feather.SHUFFLE, "Có kết nối qua relay", "chip-warning");
        } else {
            connection = chip(Feather.ZAP, "P2P trực tiếp", "chip-success");
        }

        long unverified = peers.stream().filter(p -> !p.isVerified()).count();
        Label verification = unverified == 0
                ? chip(Feather.CHECK_CIRCLE, "Đã xác thực", "chip-success")
                : chip(Feather.ALERT_TRIANGLE, unverified + " người chưa xác thực", "chip-warning");

        Label encryption = chip(Feather.LOCK, "Mã hoá đầu-cuối · AES-256-GCM", "chip-success");
        return List.of(connection, verification, encryption);
    }

    private static Label chip(Feather icon, String text, String variant) {
        Label chip = new Label(text, new FontIcon(icon));
        chip.getStyleClass().addAll("status-chip", variant);
        return chip;
    }
}
