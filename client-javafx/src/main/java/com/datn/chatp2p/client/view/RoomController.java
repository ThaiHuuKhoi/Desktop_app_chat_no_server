package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessionListener;
import com.datn.chatp2p.client.session.RoomConfig;
import com.datn.chatp2p.client.session.SessionPeer;
import com.datn.chatp2p.client.session.SessionPeer.ConnectionKind;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.client.view.TranscriptEntry.NoticeKind;
import com.datn.chatp2p.client.view.TranscriptEntry.SystemNotice;
import com.datn.chatp2p.client.view.TranscriptEntry.UserMessage;
import com.datn.chatp2p.common.model.ChatMessage;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Man hinh phong chat (De-cuong-Chat-P2P-Java.md muc 5): cot trai la phong +
 * nguoi trong phong, ben phai la khung chat voi dong trang thai bao mat.
 *
 * <p>Chi noi chuyen voi tang mang qua {@link ChatSession} - khong goi truc
 * tiep ma hoa hay DataChannel. Khi tich hop {@code RoomSession} cua p2p-core,
 * lop nay giu nguyen.
 */
public class RoomController implements ChatSessionListener {

    @FXML
    private Label roomNameLabel;

    @FXML
    private Label roomSubtitleLabel;

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

    private final ObservableList<PeerItem> peers = FXCollections.observableArrayList();
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
        emptyStateRoomLabel.setText(config.roomName());
        selfNameLabel.setText(config.userName());
        selfAvatarHolder.getChildren().setAll(Avatars.create(config.userName(), 16));

        peerListView.setItems(peers);
        peerListView.setCellFactory(list -> new PeerListCell(this::openDirectMessage));
        peerListView.setPlaceholder(new Label("Chưa có ai khác"));

        transcriptManager = new ChatTranscriptManager(
                session.selfPeerId(),
                peerId -> displayNames.getOrDefault(peerId, "Người lạ"));
        transcriptManager.configure(chatTabs, transcriptListView);

        // Dong cua so khi dang trong phong cung phai roi phong dang hoang (dong ket noi P2P/signaling)
        stage.setOnCloseRequest(event -> session.leave());

        notice(NoticeKind.INFO, "Bạn đã vào phòng " + config.roomName()
                + (config.demoMode() ? " (chế độ demo)" : ""));
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
            peerListView.refresh();
            refreshRoomState();
        });
    }

    @Override
    public void onPeerLeft(String peerId) {
        Platform.runLater(() -> {
            peers.removeIf(item -> item.peer.peerId().equals(peerId));
            pendingPeerUpdates.remove(peerId);
            notice(NoticeKind.LEFT, displayNames.getOrDefault(peerId, "Một người") + " đã rời phòng");
            refreshRoomState();
        });
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        Platform.runLater(() -> {
            transcriptManager.addGroup(new UserMessage(message));
            refreshRoomState();
        });
    }

    @Override
    public void onDirectMessageReceived(String peerId, ChatMessage message) {
        Platform.runLater(() -> {
            transcriptManager.addDirect(
                    peerId,
                    displayNames.getOrDefault(peerId, "Người lạ"),
                    message);
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
        notice(NoticeKind.INFO, "Đã sao chép tên phòng — gửi cho người bạn muốn chat cùng");
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
            session.leave();
            Navigator.showHome(stage);
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
        if (peers.stream().anyMatch(p -> p.peer.connectionKind() == ConnectionKind.DEMO)) {
            connection = chip(Feather.CPU, "Kết nối giả lập (demo)", "chip-warning");
        } else if (peers.stream().anyMatch(p -> p.peer.connectionKind() == ConnectionKind.RELAY)) {
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
