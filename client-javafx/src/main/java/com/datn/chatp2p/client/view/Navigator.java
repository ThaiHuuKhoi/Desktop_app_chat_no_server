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

    static final double HOME_WIDTH = 760;
    static final double HOME_HEIGHT = 680;
    /** Sanh cho: thanh ben danh sach phong 290 + khung xem truoc phong. */
    static final double LOBBY_WIDTH = 1100;
    static final double LOBBY_HEIGHT = 700;
    /** 3 cot: hoi thoai 260 + khung chat + nguoi trong phong 270. */
    static final double ROOM_WIDTH = 1240;
    static final double ROOM_HEIGHT = 720;

    private static final String STYLESHEET = "/css/app.css";

    private Navigator() {
    }

    /**
     * Quay ve Home sau khi roi phong, GIU NGUYEN che do luc vao phong: khach
     * (khong co token) thi van la khach, da dang nhap thi giu token va ten -
     * khong bat nguoi dung chon lai hay dang nhap lai.
     */
    static void showHomeAfterLeaving(Stage stage, RoomConfig config) {
        boolean accountMode = config.authToken() != null && !config.authToken().isBlank();
        showHome(stage, accountMode, config.userName(), config.authToken(), config.serverUri());
    }

    /**
     * Quay ve Home sau khi server tu choi cho vao phong: hien ly do, va voi sai mat khau thi mo lai hop
     * nhap mat khau cua dung phong do. Token het han (che do tai khoan) thi chuyen sang man hinh dang nhap.
     */
    static void showHomeAfterJoinRejected(Stage stage, RoomConfig config, String reason) {
        boolean accountMode = config.authToken() != null && !config.authToken().isBlank();
        if (LobbyRules.requiresLogin(reason) && accountMode) {
            showAuth(stage);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING, LobbyRules.joinRejectedMessage(reason, config.roomName()));
            alert.initOwner(stage);
            alert.setHeaderText("Cần đăng nhập lại");
            style(alert.getDialogPane());
            alert.show();
            return;
        }
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/home.fxml"));
        Parent root = load(loader);
        HomeController controller = loader.getController();
        controller.setStage(stage);
        controller.setAccountMode(accountMode);
        if (accountMode) {
            controller.setAuthenticatedUser(config.userName(), config.authToken(), config.serverUri());
        } else {
            controller.setUserName(config.userName());
            controller.setServerUri(config.serverUri());
        }
        stage.setOnCloseRequest(null);
        stage.setTitle(ChatApplication.APP_TITLE);
        stage.setScene(styledScene(root, LOBBY_WIDTH, LOBBY_HEIGHT));
        controller.showJoinRejected(config, reason);
    }

    /** Man hinh dau tien: dang nhap (tiep tuc nhanh voi tai khoan da nho / form / vao lam khach). */
    public static void showEntry(Stage stage) {
        showAuth(stage);
    }

    static void showAuth(Stage stage) {
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/auth.fxml"));
        Parent root = load(loader);
        loader.<AuthController>getController().setStage(stage);

        stage.setOnCloseRequest(null);
        stage.setTitle(ChatApplication.APP_TITLE);
        stage.setScene(styledScene(root, HOME_WIDTH, HOME_HEIGHT));
    }

    static void showHome(Stage stage, boolean accountMode) {
        showHome(stage, accountMode, null, null, null);
    }

    /**
     * @param serverUri server da cap {@code authToken} (che do tai khoan) - token chi bao gio duoc gui
     *                  toi server nay; voi khach la server dang dung (null = mac dinh).
     */
    static void showHome(Stage stage, boolean accountMode, String username, String authToken, String serverUri) {
        FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/view/home.fxml"));
        Parent root = load(loader);
        HomeController controller = loader.getController();
        controller.setStage(stage);
        controller.setAccountMode(accountMode);
        if (accountMode) {
            controller.setAuthenticatedUser(username, authToken, serverUri);
        } else {
            controller.setUserName(username);
            controller.setServerUri(serverUri);
        }

        stage.setOnCloseRequest(null);
        stage.setTitle(ChatApplication.APP_TITLE);
        stage.setScene(styledScene(root, LOBBY_WIDTH, LOBBY_HEIGHT));
    }

    /**
     * @throws UnsupportedOperationException neu che do da chon chua duoc ho tro
     *         (xem {@link ChatSessions#create}) - man hinh hien tai giu nguyen.
     */
    static void showRoom(Stage stage, RoomConfig config) {
        showRoom(stage, config, ChatSessions.create(config));
    }

    /**
     * Nhu {@link #showRoom(Stage, RoomConfig)} nhung voi session DA TAO SAN - de tao session (phong co
     * mat khau ton ~0,1-0,3 s cho PBKDF2) tren luong nen, khong lam dung giao dien. Goi tren JavaFX thread.
     */
    static void showRoom(Stage stage, RoomConfig config, ChatSession session) {
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
