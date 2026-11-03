package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.AuthClient;
import com.datn.chatp2p.client.session.PeerIdentityService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class AuthController {

    private static final String DEFAULT_SERVER_URI = "ws://165.101.47.83:8080";

    @FXML
    private TextField serverField;

    @FXML
    private TextField accountField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label formTitle;

    @FXML
    private Button submitButton;

    @FXML
    private Hyperlink switchButton;

    @FXML
    private Label statusLabel;

    private final AuthClient authClient = new AuthClient();
    private final PeerIdentityService identityService = new PeerIdentityService();
    private Stage stage;
    private boolean registrationMode;

    @FXML
    private void initialize() {
        serverField.setText(DEFAULT_SERVER_URI);
        showLoginForm();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void onSubmit() {
        try {
            String serverUri = serverField.getText().trim();
            String username = accountField.getText().trim();
            String password = passwordField.getText();
            String publicKey = identityService.publicKeyBase64();

            if (registrationMode) {
                authClient.register(serverUri, username, password, publicKey);
                showLoginForm();
                statusLabel.setText("Đăng ký thành công. Hãy đăng nhập.");
            } else {
                AuthClient.AuthResult result = authClient.login(serverUri, username, password, publicKey);
                Navigator.showHome(stage, true, result.username(), result.token());
            }
        } catch (RuntimeException error) {
            statusLabel.setText(error.getMessage() == null
                    ? "Không thể thực hiện yêu cầu." : error.getMessage());
        }
    }

    @FXML
    private void onSwitchMode() {
        if (registrationMode) {
            showLoginForm();
        } else {
            showRegisterForm();
        }
    }

    @FXML
    private void onGuest() {
        Navigator.showHome(stage, false);
    }

    private void showLoginForm() {
        registrationMode = false;
        formTitle.setText("Đăng nhập");
        submitButton.setText("Đăng nhập");
        switchButton.setText("Chưa có tài khoản? Đăng ký");
        statusLabel.setText("");
    }

    private void showRegisterForm() {
        registrationMode = true;
        formTitle.setText("Đăng ký");
        submitButton.setText("Đăng ký");
        switchButton.setText("Đã có tài khoản? Đăng nhập");
        statusLabel.setText("");
    }
}
