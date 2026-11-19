package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import com.datn.chatp2p.client.session.AuthClient.RoomSummary;
import com.datn.chatp2p.client.util.RoomNameGenerator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Cac hop thoai o sanh cho: tao phong, xac nhan vao phong, nhap mat khau, vao phong bang ten. */
final class RoomDialogs {

    /** Ket qua hop tao phong / vao bang ten. {@code password} rong = phong cong khai. */
    record RoomChoice(String roomName, String password) {
    }

    private RoomDialogs() {
    }

    // ------------------------------------------------------------ tao phong

    /** Hop "Thong tin phong" khi bam Tao phong. */
    static Optional<RoomChoice> showCreate(Window owner, List<RoomSummary> activeRooms) {
        TextField nameField = new TextField(RoomNameGenerator.generate());
        HBox.setHgrow(nameField, Priority.ALWAYS);
        Button shuffle = iconButton(Feather.SHUFFLE, "Sinh tên phòng ngẫu nhiên");
        shuffle.setOnAction(e -> nameField.setText(RoomNameGenerator.generate()));
        Button copy = iconButton(Feather.COPY, "Sao chép tên phòng để gửi cho bạn bè");

        CheckBox usePassword = new CheckBox("Đặt mật khẩu cho phòng");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu (ít nhất " + LobbyRules.MIN_ROOM_PASSWORD_LENGTH + " ký tự)");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Nhập lại mật khẩu");
        VBox passwordBox = new VBox(6, password, confirm);
        passwordBox.visibleProperty().bind(usePassword.selectedProperty());
        passwordBox.managedProperty().bind(usePassword.selectedProperty());

        Label info = hint("");
        Label error = errorLabel();
        Runnable refreshInfo = () -> info.setText(LobbyRules.createInfoText(
                LobbyRules.normalizeName(nameField.getText()), usePassword.isSelected(), activeRooms));
        nameField.textProperty().addListener((o, a, b) -> refreshInfo.run());
        usePassword.selectedProperty().addListener((o, a, b) -> refreshInfo.run());
        refreshInfo.run();

        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(LobbyRules.normalizeName(nameField.getText()));
            Clipboard.getSystemClipboard().setContent(content);
            error.setText("");
            info.setText(usePassword.isSelected()
                    ? "Đã sao chép tên phòng — nhớ gửi kèm mật khẩu cho bạn bè qua một kênh khác."
                    : "Đã sao chép tên phòng.");
        });

        VBox content = new VBox(10,
                fieldLabel("Tên phòng"), new HBox(8, nameField, shuffle, copy),
                usePassword, passwordBox, info, error);
        content.setPrefWidth(420);

        ButtonType create = new ButtonType("Tạo phòng", ButtonBar.ButtonData.OK_DONE);
        Dialog<RoomChoice> dialog = dialog(owner, "Tạo phòng", "Thông tin phòng", content, create);
        // Tich "Dat mat khau" lam hien them 2 o nhap -> phai gian cua so, neu khong hang nut bi day ra ngoai.
        usePassword.selectedProperty().addListener((o, a, b) -> fitWindow(dialog));
        info.textProperty().addListener((o, a, b) -> fitWindow(dialog));
        validateOnOk(dialog, create, error, () -> LobbyRules.validateCreate(
                LobbyRules.normalizeName(nameField.getText()), usePassword.isSelected(),
                LobbyRules.normalizePassword(password.getText()), LobbyRules.normalizePassword(confirm.getText())));
        dialog.setResultConverter(button -> button == create
                ? new RoomChoice(LobbyRules.normalizeName(nameField.getText()),
                        usePassword.isSelected() ? LobbyRules.normalizePassword(password.getText()) : "")
                : null);
        Platform.runLater(nameField::requestFocus);
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------ vao phong tu danh sach

    /** Hop xac nhan khi chon 1 phong trong danh sach. */
    static boolean confirmJoin(Window owner, RoomSummary room) {
        ButtonType next = new ButtonType(room.passwordProtected() ? "Tiếp tục" : "Vào phòng", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, LobbyRules.confirmJoinText(room), next, cancel);
        alert.initOwner(owner);
        alert.setTitle("Xác nhận vào phòng");
        alert.setHeaderText("Vào phòng \"" + room.roomId() + "\"?");
        alert.setGraphic(new FontIcon(room.passwordProtected() ? Feather.LOCK : Feather.USERS));
        Navigator.style(alert.getDialogPane());
        return alert.showAndWait().orElse(cancel) == next;
    }

    /** Hop nhap mat khau cho phong co mat khau. */
    static Optional<String> askPassword(Window owner, String roomName) {
        return askPassword(owner, roomName, "");
    }

    /** Nhu {@link #askPassword(Window, String)} nhung hien san 1 dong loi (vd. "Sai mat khau" do server bao). */
    static Optional<String> askPassword(Window owner, String roomName, String initialError) {
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu phòng");
        Label error = errorLabel();
        error.setText(initialError == null ? "" : initialError);
        VBox content = new VBox(10,
                hint("Nhập mật khẩu mà người tạo phòng đã gửi cho bạn (phân biệt hoa thường)."),
                password, error);
        content.setPrefWidth(380);

        ButtonType join = new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE);
        Dialog<String> dialog = dialog(owner, "Mật khẩu phòng", "Phòng \"" + roomName + "\" có mật khẩu", content, join);
        dialog.setGraphic(new FontIcon(Feather.LOCK));
        validateOnOk(dialog, join, error,
                () -> LobbyRules.validateJoinPassword(LobbyRules.normalizePassword(password.getText())));
        dialog.setResultConverter(button -> button == join ? LobbyRules.normalizePassword(password.getText()) : null);
        Platform.runLater(password::requestFocus);
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------ vao phong bang ten

    /** Vao 1 phong da biet ten (khach khong co danh sach phong, hoac ban be gui ten qua kenh khac). */
    static Optional<RoomChoice> showJoinByName(Window owner) {
        TextField nameField = new TextField();
        nameField.setPromptText("Tên phòng bạn bè gửi cho bạn");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu (để trống nếu là phòng công khai)");
        Label error = errorLabel();
        VBox content = new VBox(10,
                fieldLabel("Tên phòng"), nameField,
                fieldLabel("Mật khẩu phòng (nếu có)"), password,
                hint("Tên phòng và mật khẩu phân biệt hoa thường. Sai mật khẩu bạn sẽ ở một phòng trống khác."),
                error);
        content.setPrefWidth(400);

        ButtonType join = new ButtonType("Vào phòng", ButtonBar.ButtonData.OK_DONE);
        Dialog<RoomChoice> dialog = dialog(owner, "Vào phòng bằng tên", "Vào phòng bằng tên", content, join);
        validateOnOk(dialog, join, error, () -> LobbyRules.validateRoomName(LobbyRules.normalizeName(nameField.getText())));
        dialog.setResultConverter(button -> button == join
                ? new RoomChoice(LobbyRules.normalizeName(nameField.getText()), LobbyRules.normalizePassword(password.getText()))
                : null);
        Platform.runLater(nameField::requestFocus);
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------ tien ich

    private static <T> Dialog<T> dialog(Window owner, String title, String header, Node content, ButtonType ok) {
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().lookupButton(ok).getStyleClass().add(Styles.ACCENT);
        Navigator.style(dialog.getDialogPane());
        return dialog;
    }

    /** Bam nut OK ma du lieu sai thi GIU hop lai va hien loi, khong dong hop. */
    private static void validateOnOk(Dialog<?> dialog, ButtonType ok, Label error, Supplier<String> validator) {
        dialog.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            String message = validator.get();
            if (message != null) {
                error.setText(message);
                event.consume();
                fitWindow(dialog); // dong loi vua hien them -> gian cua so cho van thay hang nut
            }
        });
    }

    /**
     * Tinh lai kich thuoc cua so hop thoai theo noi dung hien tai. Dialog cua JavaFX chi tu do kich
     * thuoc 1 lan luc mo; noi dung cao them sau do (o mat khau, dong loi) se bi cat mat hang nut.
     * Chay sau lan layout ke tiep de do dung chieu cao moi.
     */
    private static void fitWindow(Dialog<?> dialog) {
        Platform.runLater(() -> {
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
    }

    private static Button iconButton(Feather icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().add("button-icon");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        wrapFully(label);
        label.getStyleClass().add("field-hint");
        return label;
    }

    /** Xuong dong day du thay vi bi cat thanh "..." (Label mac dinh khong cao them cho nhieu dong). */
    private static void wrapFully(Label label) {
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }

    private static Label errorLabel() {
        Label label = new Label();
        wrapFully(label);
        label.getStyleClass().add("error-label");
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.managedProperty());
        return label;
    }
}
