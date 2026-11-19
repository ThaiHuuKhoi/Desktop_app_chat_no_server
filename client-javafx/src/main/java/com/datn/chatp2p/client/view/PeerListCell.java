package com.datn.chatp2p.client.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import com.datn.chatp2p.client.util.TrustedPeers;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 1 dong trong danh sach nguoi trong phong: avatar + cham trang thai ket noi,
 * ten, van tay rut gon, va trang thai identity tu dong.
 */
class PeerListCell extends ListCell<PeerItem> {

    private static final int SHORT_FINGERPRINT_LENGTH = 14;
    private final Consumer<PeerItem> onDirectMessageRequested;
    private final Consumer<PeerItem> onVerifyRequested;
    private final Function<PeerItem, TrustedPeers.Status> trustStatus;

    /**
     * @param onVerifyRequested bam vao huy hieu xac thuc -&gt; mo hop so van tay.
     * @param trustStatus       nguoi nay da duoc xac minh van tay truoc do chua ({@link TrustedPeers}).
     */
    PeerListCell(Consumer<PeerItem> onDirectMessageRequested, Consumer<PeerItem> onVerifyRequested,
                 Function<PeerItem, TrustedPeers.Status> trustStatus) {
        this.onDirectMessageRequested = onDirectMessageRequested;
        this.onVerifyRequested = onVerifyRequested;
        this.trustStatus = trustStatus;
        // Vua khit chieu rong cot, ten dai thi rut gon "..." thay vi hien thanh cuon ngang
        setPrefWidth(0);
    }

    @Override
    protected void updateItem(PeerItem item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        Circle onlineDot = new Circle(5);
        onlineDot.getStyleClass().add("status-dot-online");
        StackPane avatar = new StackPane(Avatars.create(item.peer.displayName(), 18), onlineDot);
        StackPane.setAlignment(onlineDot, Pos.BOTTOM_RIGHT);

        Label nameLabel = new Label(item.peer.displayName());
        nameLabel.getStyleClass().add("peer-name");

        String fingerprint = item.peer.remoteFingerprint();
        Label fingerprintLabel = new Label(fingerprint == null
                ? "Chưa có vân tay"
                : fingerprint.substring(0, Math.min(SHORT_FINGERPRINT_LENGTH, fingerprint.length())) + " …");
        fingerprintLabel.getStyleClass().add("peer-fingerprint");

        VBox textBox = new VBox(2, nameLabel, fingerprintLabel);
        textBox.setMinWidth(0);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(10, avatar, textBox);
        row.setAlignment(Pos.CENTER_LEFT);

        if (item.isVerified()) {
            TrustedPeers.Status trust = trustStatus.apply(item);
            Label badge;
            if (trust == TrustedPeers.Status.TRUSTED) {
                badge = new Label(null, new FontIcon(Feather.SHIELD));
                badge.getStyleClass().add("peer-trusted-badge");
                badge.setTooltip(new Tooltip("Đã tin cậy: bạn đã so vân tay với người này trước đây và vẫn khớp"));
            } else if (trust == TrustedPeers.Status.CHANGED) {
                badge = new Label("Khoá đã đổi", new FontIcon(Feather.ALERT_OCTAGON));
                badge.getStyleClass().add("peer-changed-badge");
                badge.setTooltip(new Tooltip("Vân tay KHÁC lần bạn đã xác minh người tên này. "
                        + "Có thể là người khác mạo danh, hoặc họ đã đổi máy. Bấm để so lại vân tay."));
            } else {
                badge = new Label(null, new FontIcon(Feather.CHECK_CIRCLE));
                badge.getStyleClass().add("peer-verified-badge");
                badge.setTooltip(new Tooltip("Chữ ký danh tính hợp lệ. Bấm để so vân tay và đánh dấu tin cậy"));
            }
            badge.setOnMouseClicked(event -> onVerifyRequested.accept(item));
            badge.setStyle("-fx-cursor: hand;");
            row.getChildren().add(badge);
        } else {
            Label status = new Label(
                    fingerprint == null ? "Đang xác minh" : "Chưa xác minh",
                    new FontIcon(Feather.ALERT_TRIANGLE));
            status.getStyleClass().add("peer-unverified-badge");
            status.setTooltip(new Tooltip("Chữ ký identity chưa được xác minh"));
            row.getChildren().add(status);
        }
        Label direct = new Label(null, new FontIcon(Feather.MESSAGE_CIRCLE));
        direct.getStyleClass().add("peer-dm-button");
        direct.setTooltip(new Tooltip("Mở tin nhắn riêng"));
        direct.setOnMouseClicked(event -> onDirectMessageRequested.accept(item));
        row.getChildren().add(direct);

        setGraphic(row);
        setText(null);
    }
}
