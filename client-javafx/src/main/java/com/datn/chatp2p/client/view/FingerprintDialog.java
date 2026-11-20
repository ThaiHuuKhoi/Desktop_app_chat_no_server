package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Hop thoai so van tay (De-cuong muc 5) - phat hien ke xen giua (MITM) o lan gap dau tien. Hien MA AN
 * TOAN CHUNG cua 2 nguoi (giong het tren 2 may): ma QR de dat 2 man hinh canh nhau, va 12 nhom 5 chu
 * so de doc to qua dien thoai ({@link SafetyCode}). Van tay day du thu gon trong muc mo rong.
 */
final class FingerprintDialog {

    enum Result { MATCH, MISMATCH, CANCELLED }

    private static final int GROUPS_PER_LINE = 4;
    private static final int NUMBER_COLUMNS = 4;

    private FingerprintDialog() {
    }

    static Result show(Window owner, PeerItem item) {
        return show(owner, item, null, false);
    }

    /**
     * @param roomName   ten phong (dong phu duoi tieu de), co the null.
     * @param keyChanged mo tu canh bao "khoa danh tinh da doi" -&gt; tieu de va giai thich rieng.
     */
    static Result show(Window owner, PeerItem item, String roomName, boolean keyChanged) {
        String peerName = item.peer.displayName();
        String combined = SafetyCode.combine(item.peer.localFingerprint(), item.peer.remoteFingerprint());

        VBox content = new VBox(16, header(item, roomName, keyChanged));
        if (keyChanged) {
            content.getChildren().add(notice(Feather.ALERT_TRIANGLE, "verify-changed",
                    "Trước đây bạn đã xác minh " + peerName + ", nhưng khoá danh tính bây giờ KHÁC. Có thể "
                            + peerName + " đã đổi máy hoặc cài lại ứng dụng, hoặc có người đang giả mạo. "
                            + "Hãy so lại mã dưới đây trước khi tin tưởng."));
        }
        content.getChildren().addAll(
                steps(peerName),
                codeRow(combined, peerName, item),
                notice(Feather.ALERT_TRIANGLE, "verify-warning",
                        "Đừng so mã qua chính phòng chat này: nếu có người xen giữa, họ cũng có thể sửa tin "
                                + "nhắn chứa mã. Không khớp thì đừng gửi thông tin nhạy cảm."));
        content.setPrefWidth(640);
        content.getStyleClass().add("verify-dialog");

        ButtonType match = new ButtonType("Khớp – đánh dấu đã xác minh", ButtonBar.ButtonData.OK_DONE);
        ButtonType mismatch = new ButtonType("Không khớp", ButtonBar.ButtonData.NO);
        ButtonType later = new ButtonType("Để sau", ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(keyChanged ? "Khoá danh tính đã đổi" : "Xác minh vân tay");
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(700);
        dialog.getDialogPane().getButtonTypes().addAll(match, mismatch, later);
        dialog.getDialogPane().lookupButton(match).getStyleClass().add(Styles.ACCENT);
        // Nut "Khop" chu dai: khong ep cung do rong voi 2 nut kia (se bi cat chu)
        ButtonBar.setButtonUniformSize(dialog.getDialogPane().lookupButton(match), false);
        dialog.getDialogPane().lookupButton(mismatch).getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
        ((Button) dialog.getDialogPane().lookupButton(later)).setDefaultButton(false);
        Navigator.style(dialog.getDialogPane());

        ButtonType chosen = dialog.showAndWait().orElse(later);
        if (chosen == match) {
            return Result.MATCH;
        }
        return chosen == mismatch ? Result.MISMATCH : Result.CANCELLED;
    }

    private static Node header(PeerItem item, String roomName, boolean keyChanged) {
        String peerName = item.peer.displayName();
        Label title = new Label(keyChanged ? "Khoá của " + peerName + " đã đổi" : "Xác minh " + peerName);
        title.getStyleClass().add("verify-title");
        Label subtitle = new Label((keyChanged ? "Cần so lại vân tay" : "Chưa xác minh")
                + (roomName == null ? "" : " · phòng " + roomName));
        subtitle.getStyleClass().add("verify-subtitle");
        VBox text = new VBox(3, title, subtitle);
        HBox header = new HBox(14, Avatars.create(peerName, 22), text);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static Node steps(String peerName) {
        HBox steps = new HBox(10,
                step(1, "Gọi điện hoặc gặp trực tiếp " + peerName),
                step(2, "Cùng mở hộp này và so mã bên dưới"),
                step(3, "Giống hệt thì bấm Khớp"));
        steps.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        return steps;
    }

    private static Node step(int number, String text) {
        Label badge = new Label(String.valueOf(number));
        badge.getStyleClass().add("verify-step-number");
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        label.getStyleClass().add("verify-step-text");
        HBox step = new HBox(8, badge, label);
        step.setAlignment(Pos.TOP_LEFT);
        // prefWidth 0 + hgrow: 3 buoc chia deu chieu ngang, chu tu xuong dong thay vi bi cat "..."
        step.setPrefWidth(0);
        step.setMaxWidth(Double.MAX_VALUE);
        return step;
    }

    /** Ma QR (trai) + ma so 12 nhom (phai) + van tay day du thu gon. */
    private static Node codeRow(String combined, String peerName, PeerItem item) {
        StackPane qr = new StackPane(qrImage(combined));
        qr.getStyleClass().add("verify-qr");
        Label qrCaption = new Label("Đặt hai màn hình cạnh nhau");
        qrCaption.getStyleClass().add("verify-qr-caption");
        VBox qrColumn = new VBox(6, qr, qrCaption);
        qrColumn.setAlignment(Pos.TOP_CENTER);

        Label numbersTitle = new Label("Mã an toàn chung");
        numbersTitle.getStyleClass().add("verify-section-title");
        GridPane numbers = new GridPane();
        numbers.setHgap(14);
        numbers.setVgap(8);
        numbers.getStyleClass().add("safety-number-grid");
        List<String> groups = SafetyCode.numberGroups(combined);
        for (int i = 0; i < groups.size(); i++) {
            Label group = new Label(groups.get(i));
            group.getStyleClass().add("safety-number");
            numbers.add(group, i % NUMBER_COLUMNS, i / NUMBER_COLUMNS);
        }
        Label hint = new Label("Trên máy của " + peerName + " mã này phải giống hệt, cùng thứ tự. "
                + "Đọc to từng nhóm 5 số cho nhau.");
        hint.setWrapText(true);
        hint.setMinHeight(Label.USE_PREF_SIZE);
        hint.getStyleClass().add("verify-hint");

        VBox full = new VBox(8,
                fingerprintBlock("Bạn", item.peer.localFingerprint()),
                fingerprintBlock(peerName, item.peer.remoteFingerprint()));
        TitledPane details = new TitledPane("Xem vân tay đầy đủ của hai bên", full);
        details.setExpanded(false);
        details.getStyleClass().add("verify-details");

        VBox right = new VBox(10, numbersTitle, numbers, hint, details);
        right.setPrefWidth(0); // lay phan con lai cua hang, chu xuong dong theo do rong do
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox row = new HBox(20, qrColumn, right);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static ImageView qrImage(String combined) {
        boolean[][] cells = SafetyCode.qrMatrix(combined);
        int scale = Math.max(2, 168 / cells.length);
        WritableImage image = new WritableImage(cells.length * scale, cells.length * scale);
        PixelWriter pixels = image.getPixelWriter();
        for (int y = 0; y < cells.length; y++) {
            for (int x = 0; x < cells[y].length; x++) {
                Color color = cells[y][x] ? Color.BLACK : Color.WHITE;
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        pixels.setColor(x * scale + dx, y * scale + dy, color);
                    }
                }
            }
        }
        return new ImageView(image);
    }

    private static Node fingerprintBlock(String owner, String fingerprint) {
        Label title = new Label(owner);
        title.getStyleClass().add("verify-section-title");
        Label value = new Label(fingerprint == null ? "(chưa có)" : asGrid(fingerprint));
        value.getStyleClass().add("fingerprint-value");
        return new VBox(4, title, value);
    }

    private static Node notice(Feather icon, String styleClass, String text) {
        Label label = new Label(text, new FontIcon(icon));
        label.setWrapText(true);
        label.setMinHeight(Label.USE_PREF_SIZE);
        label.getStyleClass().addAll("verify-notice", styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
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
