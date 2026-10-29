package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

import java.util.function.Consumer;

/**
 * 1 dong trong danh sach nguoi trong phong: avatar + cham trang thai ket noi,
 * ten, van tay rut gon, va nut xac thuc van tay (De-cuong muc 5: "xac thuc
 * doi phuong qua van tay khoa cong khai").
 */
class PeerListCell extends ListCell<PeerItem> {

    private static final int SHORT_FINGERPRINT_LENGTH = 14;

    private final Consumer<PeerItem> onVerifyRequested;

    PeerListCell(Consumer<PeerItem> onVerifyRequested) {
        this.onVerifyRequested = onVerifyRequested;
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
            Label verifiedBadge = new Label(null, new FontIcon(Feather.CHECK_CIRCLE));
            verifiedBadge.getStyleClass().add("peer-verified-badge");
            verifiedBadge.setTooltip(new Tooltip("Đã xác thực vân tay"));
            row.getChildren().add(verifiedBadge);
        } else {
            Button verifyButton = new Button("Xác thực", new FontIcon(Feather.SHIELD));
            verifyButton.getStyleClass().addAll(Styles.SMALL, Styles.BUTTON_OUTLINED, Styles.WARNING);
            verifyButton.setTooltip(new Tooltip("So sánh vân tay khoá công khai với người này"));
            verifyButton.setDisable(fingerprint == null);
            verifyButton.setOnAction(e -> onVerifyRequested.accept(item));
            row.getChildren().add(verifyButton);
        }

        setGraphic(row);
        setText(null);
    }
}
