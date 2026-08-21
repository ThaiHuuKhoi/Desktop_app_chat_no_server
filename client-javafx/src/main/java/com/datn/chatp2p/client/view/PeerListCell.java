package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.model.Peer;
import com.datn.chatp2p.common.model.PeerVerificationState;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.util.function.Consumer;

/**
 * Mot dong trong danh sach peer: avatar chu cai dau, ten hien thi, fingerprint
 * rut gon, va nut xac thuc thu cong (De-cuong-Chat-P2P-Java.md muc 5: "xac
 * thuc doi phuong qua van tay khoa cong khai").
 */
class PeerListCell extends ListCell<Peer> {

    private final Consumer<Peer> onVerifyRequested;

    PeerListCell(Consumer<Peer> onVerifyRequested) {
        this.onVerifyRequested = onVerifyRequested;
    }

    @Override
    protected void updateItem(Peer peer, boolean empty) {
        super.updateItem(peer, empty);
        if (empty || peer == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        Circle avatar = new Circle(16);
        avatar.getStyleClass().add("peer-avatar");
        Text initial = new Text(initialOf(peer.getCustomUsername()));
        initial.getStyleClass().add("peer-avatar-initial");
        StackPane avatarStack = new StackPane(avatar, initial);

        Label nameLabel = new Label(peer.getCustomUsername());
        nameLabel.getStyleClass().add("peer-name");

        String shortFingerprint = peer.getPublicKeyFingerprint() == null
                ? "..."
                : peer.getPublicKeyFingerprint().substring(0, Math.min(14, peer.getPublicKeyFingerprint().length()));
        Label fingerprintLabel = new Label(shortFingerprint);
        fingerprintLabel.getStyleClass().add("peer-fingerprint");

        VBox textBox = new VBox(nameLabel, fingerprintLabel);
        textBox.setSpacing(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox row = new HBox(10, avatarStack, textBox);
        row.setAlignment(Pos.CENTER_LEFT);

        if (peer.getVerificationState() == PeerVerificationState.VERIFIED) {
            Label verifiedBadge = new Label("Da xac thuc");
            verifiedBadge.getStyleClass().add("peer-verified-badge");
            row.getChildren().add(verifiedBadge);
        } else {
            Button verifyButton = new Button("Xac thuc");
            verifyButton.getStyleClass().add("peer-verify-button");
            verifyButton.setOnAction(e -> onVerifyRequested.accept(peer));
            row.getChildren().add(verifyButton);
        }

        setGraphic(row);
        setText(null);
    }

    private static String initialOf(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
    }
}
