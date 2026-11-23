package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.ChatApplication;
import com.datn.chatp2p.client.session.ChatSession;
import com.datn.chatp2p.client.session.ChatSessions;
import com.datn.chatp2p.client.session.RoomConfig;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
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

    static final double MIN_WINDOW_WIDTH = 560;
    static final double MIN_WINDOW_HEIGHT = 560;
    /** Scene khong tinh vien + thanh tieu de cua Windows - chua san phan nay khi cat theo man hinh. */
    private static final double FRAME_RESERVE_WIDTH = 16;
    private static final double FRAME_RESERVE_HEIGHT = 48;

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
        showScene(stage, root, LOBBY_WIDTH, LOBBY_HEIGHT);
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
        showScene(stage, root, HOME_WIDTH, HOME_HEIGHT);
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
        showScene(stage, root, LOBBY_WIDTH, LOBBY_HEIGHT);
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
        showScene(stage, root, ROOM_WIDTH, ROOM_HEIGHT);
    }

    /** Gan stylesheet cua app cho hop thoai (Dialog/Alert co Scene rieng). */
    static void style(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(stylesheetUrl());
    }

    /**
     * Kich thuoc toi thieu cua cua so, nhung khong lon hon vung dung duoc cua man hinh - laptop
     * 1366x768 de scale 150% chi con ~910x500 px logic, min 560 cao se day cua so tran duoi taskbar.
     */
    public static void applyMinSize(Stage stage) {
        Rectangle2D area = usableArea(stage);
        stage.setMinWidth(Math.min(MIN_WINDOW_WIDTH, area.getWidth()));
        stage.setMinHeight(Math.min(MIN_WINDOW_HEIGHT, area.getHeight()));
    }

    /**
     * Doi man hinh: kich thuoc mong muon (vd. phong 1240x720) bi cat theo vung dung duoc cua man hinh
     * dang chua cua so (tru taskbar, tru vien + thanh tieu de), roi keo cua so vao trong man hinh neu
     * no lo ra ngoai. Cua so dang phong to / toan man hinh thi de nguyen.
     */
    private static void showScene(Stage stage, Parent root, double width, double height) {
        Rectangle2D area = usableArea(stage);
        double fittedWidth = fit(width, area.getWidth() - FRAME_RESERVE_WIDTH);
        double fittedHeight = fit(height, area.getHeight() - FRAME_RESERVE_HEIGHT);
        stage.setScene(styledScene(root, fittedWidth, fittedHeight));
        if (stage.isShowing() && !stage.isMaximized() && !stage.isFullScreen()) {
            // Cho cua so co kich thuoc moi roi moi kiem tra vi tri
            Platform.runLater(() -> keepInside(stage, area));
        }
    }

    /** Lay kich thuoc mong muon nhung khong vuot qua cho co; {@code available} am (man hinh qua nho) thi lay 1. */
    static double fit(double desired, double available) {
        return Math.max(1, Math.min(desired, available));
    }

    /** Vung dung duoc (tru taskbar) cua man hinh dang chua cua so; chua hien lan nao thi lay man hinh chinh. */
    private static Rectangle2D usableArea(Stage stage) {
        double x = stage.getX();
        double y = stage.getY();
        if (!Double.isNaN(x) && !Double.isNaN(y)) {
            double w = Double.isNaN(stage.getWidth()) ? 1 : Math.max(1, stage.getWidth());
            double h = Double.isNaN(stage.getHeight()) ? 1 : Math.max(1, stage.getHeight());
            List<Screen> screens = Screen.getScreensForRectangle(x, y, w, h);
            if (!screens.isEmpty()) {
                return screens.get(0).getVisualBounds();
            }
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static void keepInside(Stage stage, Rectangle2D area) {
        if (stage.getWidth() > area.getWidth()) {
            stage.setWidth(area.getWidth());
        }
        if (stage.getHeight() > area.getHeight()) {
            stage.setHeight(area.getHeight());
        }
        double x = Math.max(area.getMinX(), Math.min(stage.getX(), area.getMaxX() - stage.getWidth()));
        double y = Math.max(area.getMinY(), Math.min(stage.getY(), area.getMaxY() - stage.getHeight()));
        if (x != stage.getX()) {
            stage.setX(x);
        }
        if (y != stage.getY()) {
            stage.setY(y);
        }
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
