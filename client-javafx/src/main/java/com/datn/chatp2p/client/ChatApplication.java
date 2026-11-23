package com.datn.chatp2p.client;

import com.datn.chatp2p.client.util.ConsoleLogging;
import com.datn.chatp2p.client.util.DesktopNotifier;
import com.datn.chatp2p.client.util.LinkOpener;
import com.datn.chatp2p.client.util.ThemeManager;
import com.datn.chatp2p.client.util.WindowsIceFirewallPermission;
import com.datn.chatp2p.client.view.Navigator;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.logging.Logger;

/**
 * Diem khoi dong ung dung desktop JavaFX (De-cuong-Chat-P2P-Java.md muc 5).
 *
 * <p>Chay bang {@code mvn -pl client-javafx javafx:run} (xem README.md o thu
 * muc goc). Man hinh dau tien cho phep chon che do khach hoac tai khoan.
 */
public class ChatApplication extends Application {

    public static final String APP_TITLE = "Chat P2P Java";

    static {
        // Cai dat log TRUOC moi lop khac tao Logger - xem ConsoleLogging.
        ConsoleLogging.install(Boolean.getBoolean("chat.debug"));
    }

    private static final Logger LOG = Logger.getLogger(ChatApplication.class.getName());

    @Override
    public void start(Stage primaryStage) {
        if (com.datn.chatp2p.common.log.SecretLogging.enabled()) {
            LOG.warning(com.datn.chatp2p.common.log.SecretLogging.WARNING_BANNER);
        }
        LOG.info(() -> "Khoi dong " + APP_TITLE + " (Java " + System.getProperty("java.version")
                + ", theme=" + ThemeManager.current() + ", debug=" + Boolean.getBoolean("chat.debug") + ")");
        WindowsIceFirewallPermission.requestIfNeeded();
        LinkOpener.init(getHostServices());
        ThemeManager.applyCurrentTheme();
        Navigator.applyMinSize(primaryStage);
        Navigator.showEntry(primaryStage);
        primaryStage.show();
    }

    @Override
    public void stop() {
        LOG.info("Thoat ung dung");
        DesktopNotifier.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
