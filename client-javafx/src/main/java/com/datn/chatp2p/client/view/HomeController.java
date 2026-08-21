package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.ChatApplication;
import com.datn.chatp2p.client.util.RoomNameGenerator;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Man hinh dau tien: nhap ten hien thi va ten phong roi vao Room
 * (De-cuong-Chat-P2P-Java.md muc 5 - "tao/tham gia phong chat").
 */
public class HomeController {

    private static final SecureRandom RANDOM = new SecureRandom();

    @FXML
    private TextField userNameField;

    @FXML
    private TextField roomNameField;

    @FXML
    private Label errorLabel;

    private Stage stage;

    @FXML
    private void initialize() {
        userNameField.setText("Khach-" + (1000 + RANDOM.nextInt(9000)));
        roomNameField.setText(RoomNameGenerator.generate());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void onGenerateRoomName() {
        roomNameField.setText(RoomNameGenerator.generate());
    }

    @FXML
    private void onJoinRoom() {
        String userName = userNameField.getText() == null ? "" : userNameField.getText().trim();
        String roomName = roomNameField.getText() == null ? "" : roomNameField.getText().trim();

        if (userName.isEmpty() || roomName.isEmpty()) {
            errorLabel.setText("Vui long nhap ten hien thi va ten phong.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/room.fxml"));
            Parent root = loader.load();

            RoomController controller = loader.getController();
            controller.start(stage, userName, roomName);

            Scene scene = new Scene(root, 760, 520);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

            stage.setTitle(ChatApplication.APP_TITLE + " - " + roomName);
            stage.setScene(scene);
        } catch (IOException e) {
            errorLabel.setText("Khong the mo phong: " + e.getMessage());
        }
    }
}
