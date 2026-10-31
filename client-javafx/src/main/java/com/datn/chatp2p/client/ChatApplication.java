package com.datn.chatp2p.client;

import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.client.view.Navigator;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Diem khoi dong ung dung desktop JavaFX (De-cuong-Chat-P2P-Java.md muc 5).
 *
 * <p>Chay bang {@code mvn -pl client-javafx javafx:run} (xem README.md o thu
 * muc goc). Man hinh dau tien cho phep chon che do khach hoac tai khoan.
 */
public class ChatApplication extends Application {

    public static final String APP_TITLE = "Chat P2P Java";

    @Override
    public void start(Stage primaryStage) {
        ThemeManager.applyCurrentTheme();
        primaryStage.setMinWidth(560);
        primaryStage.setMinHeight(560);
        Navigator.showEntry(primaryStage);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
