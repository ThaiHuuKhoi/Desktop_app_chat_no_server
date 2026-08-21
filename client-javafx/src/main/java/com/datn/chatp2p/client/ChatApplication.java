package com.datn.chatp2p.client;

import com.datn.chatp2p.client.view.HomeController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Diem khoi dong ung dung desktop JavaFX (De-cuong-Chat-P2P-Java.md muc 5).
 *
 * <p>Chay bang {@code mvn -pl client-javafx javafx:run} (xem README.md o thu
 * muc goc). Man hinh dau tien la Home (tao/tham gia phong); {@link HomeController}
 * chuyen sang man hinh Room khi nguoi dung xac nhan.
 */
public class ChatApplication extends Application {

    public static final String APP_TITLE = "Chat P2P Java";

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/home.fxml"));
        Parent root = loader.load();

        HomeController controller = loader.getController();
        controller.setStage(primaryStage);

        Scene scene = new Scene(root, 480, 360);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
