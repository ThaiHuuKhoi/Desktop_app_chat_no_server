package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Hop thoai so sanh van tay khoa cong khai (De-cuong muc 5) - co che phat
 * hien ke xen giua (MITM) o buoc trao khoa ECDH. Hien van tay dang luoi 4x4
 * nhom de doc to cho nhau qua dien thoai.
 */
final class FingerprintDialog {

    enum Result { MATCH, MISMATCH, CANCELLED }

    private static final int GROUPS_PER_LINE = 4;

    private FingerprintDialog() {
    }

    static Result show(Window owner, PeerItem item) {
        String peerName = item.peer.displayName();

        Label intro = new Label("Đọc hai dãy vân tay dưới đây cho " + peerName
                + " qua một kênh khác (gọi điện, gặp trực tiếp). Trên máy của " + peerName
                + ", hai dãy này phải giống hệt — chỉ đổi chỗ cho nhau.");
        intro.setWrapText(true);

        Label warning = new Label("Nếu không khớp, có thể có người đang xen giữa cuộc trò chuyện. "
                + "Đừng gửi thông tin nhạy cảm.", new FontIcon(Feather.ALERT_TRIANGLE));
        warning.setWrapText(true);
        warning.getStyleClass().add("fingerprint-warning");

        VBox content = new VBox(14,
                intro,
                block("Vân tay khoá của bạn", item.peer.localFingerprint()),
                block("Vân tay khoá của " + peerName, item.peer.remoteFingerprint()),
                warning);
        content.setPrefWidth(460);

        ButtonType match = new ButtonType("Khớp – Xác thực", ButtonBar.ButtonData.OK_DONE);
        ButtonType mismatch = new ButtonType("Không khớp", ButtonBar.ButtonData.NO);
        ButtonType later = new ButtonType("Để sau", ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Xác thực vân tay");
        dialog.setHeaderText("Xác thực " + peerName);
        dialog.setGraphic(new FontIcon(Feather.SHIELD));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(match, mismatch, later);
        dialog.getDialogPane().lookupButton(match).getStyleClass().add(Styles.SUCCESS);
        dialog.getDialogPane().lookupButton(mismatch).getStyleClass().add(Styles.DANGER);
        ((Button) dialog.getDialogPane().lookupButton(later)).setDefaultButton(false);
        Navigator.style(dialog.getDialogPane());

        ButtonType chosen = dialog.showAndWait().orElse(later);
        if (chosen == match) {
            return Result.MATCH;
        }
        return chosen == mismatch ? Result.MISMATCH : Result.CANCELLED;
    }

    private static VBox block(String title, String fingerprint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("fingerprint-title");
        Label value = new Label(fingerprint == null ? "(chưa có)" : asGrid(fingerprint));
        value.getStyleClass().add("fingerprint-value");
        VBox block = new VBox(6, titleLabel, value);
        block.getStyleClass().add("fingerprint-block");
        return block;
    }

    /** "A1B2 C3D4 ..." (16 nhom) -> 4 dong x 4 nhom. */
    static String asGrid(String fingerprint) {
        String[] groups = fingerprint.trim().split("\\s+");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < groups.length; i += GROUPS_PER_LINE) {
            lines.add(String.join("  ", List.of(groups).subList(i, Math.min(i + GROUPS_PER_LINE, groups.length))));
        }
        return String.join("\n", lines);
    }
}
