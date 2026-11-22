package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import com.datn.chatp2p.client.session.SessionPeer;
import com.datn.chatp2p.client.util.TrustedPeers;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 1 nguoi trong cot phai: avatar, ten, nut nhan rieng; dong trang thai xac minh + duong truyen
 * (truc tiep / qua relay); nut "So vân tay" khi chua so. Khoa danh tinh doi -&gt; ca o canh bao.
 */
class PeerListCell extends ListCell<PeerItem> {

    private final Consumer<PeerItem> onDirectMessageRequested;
    private final Consumer<PeerItem> onVerifyRequested;
    private final Function<PeerItem, TrustedPeers.Status> trustStatus;
    private final java.util.function.Predicate<PeerItem> mismatched;
    private final java.util.function.Predicate<PeerItem> guestNameClash;

    /**
     * @param onVerifyRequested bam "So vân tay" -&gt; mo hop so van tay.
     * @param trustStatus       nguoi nay da duoc xac minh van tay truoc do chua ({@link TrustedPeers}).
     * @param mismatched        nguoi dung da bam "Không khớp" khi so van tay voi nguoi nay trong phien.
     * @param guestNameClash    khach nay dat trung ten mot tai khoan dang co trong phong (ke ca minh).
     */
    PeerListCell(Consumer<PeerItem> onDirectMessageRequested, Consumer<PeerItem> onVerifyRequested,
                 Function<PeerItem, TrustedPeers.Status> trustStatus,
                 java.util.function.Predicate<PeerItem> mismatched,
                 java.util.function.Predicate<PeerItem> guestNameClash) {
        this.onDirectMessageRequested = onDirectMessageRequested;
        this.onVerifyRequested = onVerifyRequested;
        this.trustStatus = trustStatus;
        this.mismatched = mismatched;
        this.guestNameClash = guestNameClash;
        // Vua khit chieu rong cot, ten dai thi rut gon "..." thay vi hien thanh cuon ngang
        setPrefWidth(0);
    }

    @Override
    protected void updateItem(PeerItem item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        Label nameLabel = new Label(item.peer.displayName());
        nameLabel.getStyleClass().add("peer-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Button direct = new Button(null, new FontIcon(Feather.MESSAGE_CIRCLE));
        direct.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, "peer-dm-button");
        direct.setTooltip(new Tooltip("Nhắn riêng cho " + item.peer.displayName()));
        direct.setOnAction(event -> onDirectMessageRequested.accept(item));
        HBox nameRow = new HBox(4, nameLabel, direct);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        boolean isMismatched = mismatched.test(item);
        TrustedPeers.Status trust = item.isVerified() ? trustStatus.apply(item) : null;
        Label verification = isMismatched ? mismatchLabel() : verificationLabel(item, trust);
        Label connection = connectionLabel(item.peer.connectionKind());
        FlowPane statusLine = new FlowPane(6, 2, verification, connection);
        Label account = accountLabel(item.peer.account(), guestNameClash.test(item));
        if (account != null) {
            statusLine.getChildren().add(account);
        }

        VBox text = new VBox(3, nameRow, statusLine);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        boolean canCompare = item.isVerified() && item.peer.remoteFingerprint() != null;
        if (canCompare && (isMismatched || trust != TrustedPeers.Status.TRUSTED)) {
            Button compare = new Button(isMismatched || trust == TrustedPeers.Status.CHANGED ? "So lại vân tay" : "So vân tay");
            compare.getStyleClass().addAll(Styles.SMALL, "peer-compare-button");
            compare.setOnAction(event -> onVerifyRequested.accept(item));
            text.getChildren().add(compare);
        } else if (trust == TrustedPeers.Status.TRUSTED) {
            // Da tin cay van mo lai duoc hop van tay (bam vao dong trang thai)
            verification.setOnMouseClicked(event -> onVerifyRequested.accept(item));
            verification.setStyle("-fx-cursor: hand;");
        }

        HBox row = new HBox(10, Avatars.create(item.peer.displayName(), 17), text);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("peer-card");
        if (isMismatched) {
            row.getStyleClass().add("peer-card-mismatch");
        } else if (trust == TrustedPeers.Status.CHANGED) {
            row.getStyleClass().add("peer-card-changed");
        } else if (trust == TrustedPeers.Status.TRUSTED) {
            row.getStyleClass().add("peer-card-trusted");
        }
        setGraphic(row);
    }

    private static Label verificationLabel(PeerItem item, TrustedPeers.Status trust) {
        Label label;
        if (!item.isVerified()) {
            label = new Label(item.peer.remoteFingerprint() == null ? "Đang xác minh…" : "Chưa xác minh chữ ký",
                    new FontIcon(Feather.LOADER));
            label.getStyleClass().add("peer-status-muted");
            label.setTooltip(new Tooltip("Đang chờ chữ ký danh tính của người này"));
        } else if (trust == TrustedPeers.Status.TRUSTED) {
            label = new Label("Đã xác minh", new FontIcon(Feather.SHIELD));
            label.getStyleClass().add("peer-status-trusted");
            label.setTooltip(new Tooltip("Bạn đã so vân tay với người này và vẫn khớp. Bấm để xem lại."));
        } else if (trust == TrustedPeers.Status.CHANGED) {
            label = new Label("Khoá danh tính đã đổi", new FontIcon(Feather.ALERT_TRIANGLE));
            label.getStyleClass().add("peer-status-changed");
            label.setTooltip(new Tooltip("Vân tay KHÁC lần bạn đã xác minh người tên này: có thể họ đổi máy, "
                    + "hoặc có người mạo danh. So lại vân tay trước khi gửi thông tin nhạy cảm."));
        } else {
            label = new Label("Chưa so vân tay");
            label.getStyleClass().add("peer-status-muted");
            label.setTooltip(new Tooltip("Chữ ký danh tính hợp lệ. So vân tay qua một kênh khác để chắc đúng người."));
        }
        return label;
    }

    private static Label mismatchLabel() {
        Label label = new Label("Không khớp – có thể có người xen giữa", new FontIcon(Feather.X_OCTAGON));
        label.getStyleClass().add("peer-status-mismatch");
        label.setWrapText(true);
        label.setTooltip(new Tooltip("Bạn đã đánh dấu vân tay KHÔNG khớp. Đừng gửi thông tin nhạy cảm; "
                + "nên rời phòng và tạo phòng mới."));
        return label;
    }

    /** "· Tài khoản" / "· Khách" theo xac nhan cua server; null neu server khong bao (server cu). */
    static Label accountLabel(SessionPeer.AccountKind kind, boolean nameClash) {
        if (kind == SessionPeer.AccountKind.ACCOUNT) {
            Label label = new Label("· Tài khoản");
            label.getStyleClass().add("peer-account-badge");
            label.setTooltip(new Tooltip("Đã đăng nhập: server xác nhận tên này là tên tài khoản. Khoá danh tính "
                    + "của người này được tự đối chiếu với khoá đã đăng ký của tài khoản."));
            return label;
        }
        if (kind == SessionPeer.AccountKind.GUEST) {
            Label label = new Label(nameClash ? "· Khách trùng tên tài khoản" : "· Khách");
            label.getStyleClass().add(nameClash ? "peer-guest-badge-clash" : "peer-guest-badge");
            label.setTooltip(new Tooltip(nameClash
                    ? "Người này vào với tư cách khách nhưng đặt trùng tên một tài khoản trong phòng. "
                    + "Đây KHÔNG phải tài khoản đó - đừng tin chỉ vì cái tên."
                    : "Vào với tư cách khách: tên do người này tự đặt, server không xác nhận."));
            return label;
        }
        return null;
    }

    private static Label connectionLabel(SessionPeer.ConnectionKind kind) {
        boolean relay = kind == SessionPeer.ConnectionKind.RELAY;
        Label label = new Label(relay ? "· Qua relay" : "· Trực tiếp");
        label.getStyleClass().add(relay ? "peer-connection-relay" : "peer-connection-direct");
        label.setTooltip(new Tooltip(relay
                ? "Đi vòng qua máy chủ TURN vì không đi thẳng được. Vẫn mã hoá đầu-cuối: TURN không đọc được nội dung."
                : "Kết nối UDP thẳng giữa hai máy."));
        return label;
    }
}
