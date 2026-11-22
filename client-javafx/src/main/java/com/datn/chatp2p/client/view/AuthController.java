package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.PeerIdentityService;
import com.datn.chatp2p.client.util.UserSettings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * Man hinh dau tien cua app.
 *
 * <ul>
 *   <li><b>Tiep tuc nhanh</b>: tai khoan dang nhap thanh cong lan truoc tren may nay (chi nho TEN trong
 *       {@link UserSettings#lastAccount()}, khong nho mat khau / token) -&gt; chi can nhap mat khau.</li>
 *   <li><b>Form day du</b>: dang nhap / dang ky (lan dau, hoac "tai khoan khac").</li>
 *   <li><b>Khach</b>: vao sanh cho khong can tai khoan.</li>
 * </ul>
 * Dang nhap tren may moi bi server bao "khoa identity khong khop" -&gt; hien huong dan nhap khoa tu may cu
 * ({@link SettingsDialog#importIdentity}).
 */
public final class AuthController {

    private static final Logger LOG = Logger.getLogger(AuthController.class.getName());
    private static final String DEFAULT_SERVER_URI = "wss://chat.zingme.fun";

    @FXML private Label headline;
    @FXML private VBox quickPane;
    @FXML private StackPane quickAvatar;
    @FXML private Label quickName;
    @FXML private Label quickFingerprint;
    @FXML private PasswordField quickPasswordField;
    @FXML private Button quickSubmit;

    @FXML private VBox fullPane;
    @FXML private ToggleButton loginTab;
    @FXML private ToggleButton registerTab;
    @FXML private TextField accountField;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmBox;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button submitButton;

    @FXML private Label statusLabel;
    @FXML private VBox keyMismatchBox;
    @FXML private Button otherAccountButton;
    @FXML private Button backToQuickButton;
    @FXML private Hyperlink forgetLink;
    @FXML private TextField serverField;

    private final AuthClient authClient = new AuthClient();
    private PeerIdentityService identityService = new PeerIdentityService();
    private Stage stage;
    private boolean registrationMode;
    /** Ten tai khoan da nho tren may, hoac null. */
    private String rememberedAccount;

    @FXML
    private void initialize() {
        serverField.setText(DEFAULT_SERVER_URI);
        statusLabel.managedProperty().bind(statusLabel.textProperty().isNotEmpty());
        statusLabel.visibleProperty().bind(statusLabel.managedProperty());
        ToggleGroup tabs = new ToggleGroup();
        loginTab.setToggleGroup(tabs);
        registerTab.setToggleGroup(tabs);
        // Bam lai tab dang chon khong duoc bo chon (luon co 1 tab)
        tabs.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                old.setSelected(true);
            }
        });
        quickPasswordField.setOnAction(event -> onQuickSubmit());

        rememberedAccount = UserSettings.get().lastAccount();
        if (rememberedAccount != null) {
            showQuick();
        } else {
            showFull(false);
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    // ------------------------------------------------------------ hai che do hien thi

    /** The "Tiếp tục là ..." cho tai khoan da nho. */
    private void showQuick() {
        clearMessages();
        headline.setText("Chào mừng trở lại");
        quickAvatar.getChildren().setAll(Avatars.create(rememberedAccount, 28));
        quickName.setText(rememberedAccount);
        quickFingerprint.setText("Vân tay " + LoginRules.shortFingerprint(identityService.localFingerprint()));
        quickSubmit.setText("Tiếp tục là " + rememberedAccount);
        quickPasswordField.clear();
        setVisible(quickPane, true);
        setVisible(fullPane, false);
        setVisible(otherAccountButton, true);
        setVisible(backToQuickButton, false);
        setVisible(forgetLink, true);
        quickSubmit.setDefaultButton(true);
        submitButton.setDefaultButton(false);
        Platform.runLater(quickPasswordField::requestFocus);
    }

    /** Form dang nhap / dang ky day du. */
    private void showFull(boolean register) {
        clearMessages();
        registrationMode = register;
        headline.setText(register ? "Tạo tài khoản" : rememberedAccount == null ? "Đăng nhập Chat P2P" : "Tài khoản khác");
        (register ? registerTab : loginTab).setSelected(true);
        setVisible(confirmBox, register);
        confirmPasswordField.clear();
        submitButton.setText(register ? "Đăng ký" : "Đăng nhập");
        setVisible(quickPane, false);
        setVisible(fullPane, true);
        setVisible(otherAccountButton, false);
        setVisible(backToQuickButton, rememberedAccount != null);
        backToQuickButton.setText(rememberedAccount == null ? "" : "Quay lại tài khoản " + rememberedAccount);
        setVisible(forgetLink, false);
        submitButton.setDefaultButton(true);
        quickSubmit.setDefaultButton(false);
        Platform.runLater(accountField::requestFocus);
    }

    // ------------------------------------------------------------ hanh dong

    @FXML
    private void onQuickSubmit() {
        login(rememberedAccount, quickPasswordField.getText());
    }

    @FXML
    private void onSubmit() {
        String username = accountField.getText().trim();
        String password = passwordField.getText();
        if (!registrationMode) {
            login(username, password);
            return;
        }
        clearMessages();
        String error = LoginRules.validateRegistration(username, password, confirmPasswordField.getText());
        if (error != null) {
            statusLabel.setText(error);
            return;
        }
        try {
            authClient.register(serverField.getText().trim(), username, password, identityService.publicKeyBase64());
            showFull(false);
            accountField.setText(username);
            passwordField.clear();
            statusLabel.setText("Đăng ký thành công. Hãy đăng nhập.");
            Platform.runLater(passwordField::requestFocus);
        } catch (RuntimeException e) {
            showServerError(e);
        }
    }

    private void login(String username, String password) {
        clearMessages();
        try {
            String loginServer = serverField.getText().trim();
            AuthClient.AuthResult result = authClient.login(loginServer, username, password,
                    identityService.publicKeyBase64());
            UserSettings.get().setLastAccount(result.username());
            // Truyen kem server da cap token: token chi duoc gui toi dung server nay.
            Navigator.showHome(stage, true, result.username(), result.token(), loginServer);
        } catch (RuntimeException e) {
            showServerError(e);
        }
    }

    @FXML
    private void onLoginTab() {
        showFull(false);
    }

    @FXML
    private void onRegisterTab() {
        showFull(true);
    }

    @FXML
    private void onOtherAccount() {
        showFull(false);
    }

    @FXML
    private void onBackToQuick() {
        showQuick();
    }

    /** Quen tai khoan da nho (vd may dung chung) -&gt; ve form day du. */
    @FXML
    private void onForgetAccount() {
        LOG.info(() -> "Quen tai khoan ghi nho tren may: " + rememberedAccount);
        UserSettings.get().setLastAccount(null);
        rememberedAccount = null;
        showFull(false);
    }

    /** Nhap khoa danh tinh tu may cu, roi dung khoa moi de dang nhap luon. */
    @FXML
    private void onImportKey() {
        if (SettingsDialog.importIdentity(stage)) {
            identityService = new PeerIdentityService();
            clearMessages();
            statusLabel.setText("Đã nhập khoá. Hãy đăng nhập lại.");
            if (quickPane.isVisible()) {
                quickFingerprint.setText("Vân tay " + LoginRules.shortFingerprint(identityService.localFingerprint()));
            }
        }
    }

    @FXML
    private void onGuest() {
        Navigator.showHome(stage, false);
    }

    // ------------------------------------------------------------ tien ich

    private void showServerError(RuntimeException e) {
        String message = e.getMessage() == null ? "Không thể thực hiện yêu cầu." : e.getMessage();
        if (LoginRules.isKeyMismatch(message)) {
            statusLabel.setText("");
            setVisible(keyMismatchBox, true);
        } else {
            statusLabel.setText(message);
        }
    }

    private void clearMessages() {
        statusLabel.setText("");
        setVisible(keyMismatchBox, false);
    }

    private static void setVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
