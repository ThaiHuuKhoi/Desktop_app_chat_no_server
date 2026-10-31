package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.ChatApplication;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessions;
import com.datn.chatp2p.client.session.RoomConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Chuyen qua lai giua man hinh Home va Room - gom 1 cho thay vi lap lai trong tung controller. */
public final class Navigator {

    static final double HOME_WIDTH = 600;
    static final double HOME_HEIGHT = 680;
    static final double ROOM_WIDTH = 1040;
    static final double ROOM_HEIGHT = 680;

    private static final String STYLESHEET = "/css/app.css";

    private Navigator() {
    }

    public static void showHome(Stage stage) {
        showHome(stage, true);
    }

    public static void showEntry(Stage stage) {
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/entry.fxml"));
        Parent root = load(loader);
        loader.<EntryController>getController().setStage(stage);
        stage.setOnCloseRequest(null);
        stage.setTitle(ChatApplication.APP_TITLE);
        stage.setScene(styledScene(root, HOME_WIDTH, HOME_HEIGHT));
    }

    static void showHome(Stage stage, boolean accountMode) {
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/home.fxml"));
        Parent root = load(loader);
        HomeController controller = loader.getController();
        controller.setStage(stage);
        controller.setAccountMode(accountMode);

        stage.setOnCloseRequest(null);
        stage.setTitle(ChatApplication.APP_TITLE);
        stage.setScene(styledScene(root, HOME_WIDTH, HOME_HEIGHT));
    }

    /**
     * @throws UnsupportedOperationException neu che do da chon chua duoc ho tro
     *         (xem {@link ChatSessions#create}) - man hinh hien tai giu nguyen.
     */
    static void showRoom(Stage stage, RoomConfig config) {
        ChatSession session = ChatSessions.create(config);

        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/room.fxml"));
        Parent root = load(loader);
        loader.<RoomController>getController().start(stage, config, session);

        stage.setTitle(ChatApplication.APP_TITLE + " - " + config.roomName());
        stage.setScene(styledScene(root, ROOM_WIDTH, ROOM_HEIGHT));
    }

    /** Gan stylesheet cua app cho hop thoai (Dialog/Alert co Scene rieng). */
    static void style(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(stylesheetUrl());
    }

    private static Scene styledScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(stylesheetUrl());
        return scene;
    }

    private static String stylesheetUrl() {
        return Objects.requireNonNull(Navigator.class.getResource(STYLESHEET)).toExternalForm();
    }

    private static Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Khong nap duoc " + loader.getLocation(), e);
        }
    }
}
