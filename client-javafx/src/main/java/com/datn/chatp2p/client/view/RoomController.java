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
import javafx.scene.control.Dialog;
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
    private final ReactionStore reactions = new ReactionStore();
    /** Tin da bi tac gia sua (false) hoac xoa (true). */
    private final Map<String, Boolean> editedOrDeleted = new HashMap<>();
    /** Tin nguoi khac gui ma minh chua "xem": khoa = null (khung nhom) hoac peerId (tab rieng). */
    private final Map<String, List<ChatMessage>> unreadByTab = new HashMap<>();
    /** Tin dang duoc tra loi (hien thanh "Dang tra loi ..." tren o nhap), hoac null. */
    private ChatMessage replyingTo;
    private final Label replyBarLabel = new Label();
    private final HBox replyBar = new HBox(8);
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
                new RoomMessageContext());
        transcriptManager.configure(chatTabs, transcriptListView);
        installFileDrop();

        typingNotifier = new TypingNotifier(session::sendTypingStatus, System::currentTimeMillis);
        messageArea.textProperty().addListener((obs, old, text) -> typingNotifier.onTextChanged(
                transcriptManager.selectedDirectPeerId(), text == null || text.isBlank()));
        chatTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> {
            typingNotifier.stop(); // dang go o tab cu -> bao ngung truoc khi sang tab moi
            refreshTypingLabel();
            flushReadReceipts(); // vua mo tab -> nhung tin trong tab nay coi nhu da xem
        });
        // Quay lai cua so app -> bao da xem cac tin dang hien
        stage.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                flushReadReceipts();
            }
        });
        installReplyBar();
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
            unreadByTab.remove(peerId);
            if (deliveryStatuses.peerLeft(peerId) | reactions.peerLeft(peerId)) {
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
            queueReadReceipt(null, message);
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
            queueReadReceipt(peerId, message);
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

    // ---------- Da xem / tra loi / cam xuc ----------

    @Override
    public void onMessageRead(String messageId, String peerId) {
        Platform.runLater(() -> {
            if (deliveryStatuses.read(messageId, peerId)) {
                transcriptManager.refreshAll();
            }
        });
    }

    @Override
    public void onReaction(String messageId, String peerId, String emoji, boolean added) {
        Platform.runLater(() -> {
            if (reactions.apply(messageId, peerId, emoji, added)) {
                transcriptManager.refreshAll();
            }
        });
    }

    @Override
    public void onMessageEdited(String messageId, String peerId, String newText) {
        Platform.runLater(() -> {
            ChatMessage original = transcriptManager.findMessage(messageId);
            // Chi tac gia moi duoc sua / xoa tin cua ho.
            if (original == null || !original.getAuthorId().equals(peerId)) {
                return;
            }
            applyEdit(original, newText);
        });
    }

    /** Sua / xoa tin cua minh: cap nhat ngay tren may minh roi gui cho moi nguoi. */
    private void applyOwnEdit(ChatMessage message, String newText) {
        try {
            session.editMessage(transcriptManager.selectedDirectPeerId(), message.getId(), newText);
            applyEdit(message, newText);
        } catch (RuntimeException e) {
            notice(NoticeKind.WARNING, (newText == null ? "Xoá" : "Sửa") + " tin thất bại: " + e.getMessage());
        }
    }

    /** {@code newText} null = xoa. */
    private void applyEdit(ChatMessage original, String newText) {
        editedOrDeleted.put(original.getId(), newText == null);
        ChatMessage updated = new ChatMessage(original.getId(), original.getAuthorId(),
                newText == null ? original.getText() : newText, original.getTimeSent(),
                original.getTimeReceived(), original.getReplyToId());
        transcriptManager.replaceMessage(updated);
        transcriptManager.refreshAll();
    }

    /** Tin nguoi khac vua toi: ghi nho la chua xem, roi bao "da xem" ngay neu dang mo dung tab do. */
    private void queueReadReceipt(String directPeerId, ChatMessage message) {
        unreadByTab.computeIfAbsent(directPeerId, key -> new java.util.ArrayList<>()).add(message);
        flushReadReceipts();
    }

    /**
     * Chi coi la "da xem" khi cua so app dang duoc chon VA dang mo dung tab chua tin - tin den luc
     * app bi thu nho / dang o tab khac se doi toi khi nguoi dung quay lai.
     */
    private void flushReadReceipts() {
        if (!stage.isFocused() || transcriptManager == null) {
            return;
        }
        String visibleTab = transcriptManager.selectedDirectPeerId();
        List<ChatMessage> unread = unreadByTab.remove(visibleTab);
        if (unread == null || unread.isEmpty()) {
            return;
        }
        Map<String, List<String>> idsByAuthor = new HashMap<>();
        for (ChatMessage message : unread) {
            idsByAuthor.computeIfAbsent(message.getAuthorId(), key -> new java.util.ArrayList<>()).add(message.getId());
        }
        idsByAuthor.forEach((author, ids) -> session.sendReadReceipt(author, ids, visibleTab != null));
    }

    /** Thanh "Dang tra loi X: ..." ngay tren o nhap tin, an khi khong tra loi tin nao. */
    private void installReplyBar() {
        Button cancel = new Button(null, new FontIcon(Feather.X));
        cancel.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        cancel.setOnAction(event -> clearReply());
        replyBarLabel.getStyleClass().add("reply-bar-text");
        replyBarLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(replyBarLabel, javafx.scene.layout.Priority.ALWAYS);
        replyBar.getChildren().setAll(new FontIcon(Feather.CORNER_UP_LEFT), replyBarLabel, cancel);
        replyBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        replyBar.getStyleClass().add("reply-bar");
        replyBar.setVisible(false);
        replyBar.setManaged(false);
        if (messageArea.getParent() != null && messageArea.getParent().getParent() instanceof VBox inputBar) {
            inputBar.getChildren().add(0, replyBar);
        }
    }

    private void startReply(ChatMessage message) {
        replyingTo = message;
        String author = session.selfPeerId().equals(message.getAuthorId())
                ? "chính bạn"
                : displayNames.getOrDefault(message.getAuthorId(), "Người lạ");
        replyBarLabel.setText("Đang trả lời " + author + ": " + MessageTexts.snippet(message.getText()));
        replyBar.setVisible(true);
        replyBar.setManaged(true);
        messageArea.requestFocus();
    }

    private void clearReply() {
        replyingTo = null;
        replyBar.setVisible(false);
        replyBar.setManaged(false);
    }

    /** Cau noi giua o tin nhan (TranscriptCell) va trang thai cua phong. */
    private final class RoomMessageContext implements MessageContext {
        @Override
        public DeliveryStatuses.Status delivery(String messageId) {
            return deliveryStatuses.status(messageId);
        }

        @Override
        public Map<String, Integer> reactions(String messageId) {
            return reactions.counts(messageId);
        }

        @Override
        public boolean reactedBySelf(String messageId, String emoji) {
            return reactions.hasReacted(messageId, session.selfPeerId(), emoji);
        }

        @Override
        public ChatMessage findMessage(String messageId) {
            return transcriptManager.findMessage(messageId);
        }

        @Override
        public void reply(ChatMessage message) {
            startReply(message);
        }

        @Override
        public boolean isEdited(String messageId) {
            return Boolean.FALSE.equals(editedOrDeleted.get(messageId));
        }

        @Override
        public boolean isDeleted(String messageId) {
            return Boolean.TRUE.equals(editedOrDeleted.get(messageId));
        }

        @Override
        public void edit(ChatMessage message) {
            TextArea editor = new TextArea(message.getText());
            editor.setWrapText(true);
            editor.setPrefRowCount(4);
            editor.setPrefWidth(420);
            ButtonType save = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
            Dialog<String> dialog = new Dialog<>();
            dialog.initOwner(stage);
            dialog.setTitle("Sửa tin nhắn");
            dialog.setHeaderText("Sửa tin nhắn");
            dialog.getDialogPane().setContent(editor);
            dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
            dialog.setResultConverter(button -> button == save ? editor.getText().strip() : null);
            Navigator.style(dialog.getDialogPane());
            Platform.runLater(editor::requestFocus);
            dialog.showAndWait()
                    .filter(text -> !text.isEmpty() && !text.equals(message.getText()))
                    .ifPresent(text -> applyOwnEdit(message, text));
        }

        @Override
        public void delete(ChatMessage message) {
            ButtonType remove = new ButtonType("Xoá", ButtonBar.ButtonData.OK_DONE);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Tin sẽ bị xoá ở máy mọi người trong phòng. Người khác có thể đã đọc hoặc lưu lại nội dung.",
                    remove, ButtonType.CANCEL);
            confirm.initOwner(stage);
            confirm.setHeaderText("Xoá tin nhắn này?");
            confirm.getDialogPane().lookupButton(remove).getStyleClass().add(Styles.DANGER);
            Navigator.style(confirm.getDialogPane());
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == remove) {
                applyOwnEdit(message, null);
            }
        }

        @Override
        public void toggleReaction(ChatMessage message, String emoji) {
            boolean add = !reactions.hasReacted(message.getId(), session.selfPeerId(), emoji);
            reactions.apply(message.getId(), session.selfPeerId(), emoji, add);
            transcriptManager.refreshAll();
            try {
                session.sendReaction(transcriptManager.selectedDirectPeerId(), message.getId(), emoji, add);
            } catch (RuntimeException e) {
                notice(NoticeKind.WARNING, "Không gửi được cảm xúc: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onSendMessage() {
        String text = messageArea.getText() == null ? "" : messageArea.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        ChatMessage message = replyingTo == null
                ? new ChatMessage(session.selfPeerId(), text)
                : ChatMessage.reply(session.selfPeerId(), text, replyingTo.getId());
        String directPeerId = transcriptManager.selectedDirectPeerId();
        if (directPeerId == null) {
            transcriptManager.addGroup(new UserMessage(message));
        }
        messageArea.clear();
        clearReply();
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

        @Override
        public void open(FileTransferStatus status) {
            Path file = status.savedFile();
            if (file == null || !MessageTexts.isImage(status.fileName())) {
                return; // chi mo anh, va chi khi nguoi dung bam - khong tu chay file nhan duoc
            }
            CompletableFuture.runAsync(() -> {
                try {
                    Desktop.getDesktop().open(file.toFile());
                } catch (IOException | RuntimeException e) {
                    Platform.runLater(() -> notice(NoticeKind.WARNING, "Không mở được ảnh: " + file.getFileName()));
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
