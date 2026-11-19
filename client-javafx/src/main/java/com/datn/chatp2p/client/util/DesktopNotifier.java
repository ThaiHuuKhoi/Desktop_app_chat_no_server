package com.datn.chatp2p.client.util;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/**
 * Thong bao tren man hinh (toast cua Windows / thong bao cua he dieu hanh) qua
 * {@link SystemTray} cua AWT - khong can thu vien ngoai. He dieu hanh khong ho tro khay he thong
 * thi im lang bo qua.
 *
 * <p><b>Rieng tu</b>: chi hien TEN nguoi gui, khong hien noi dung tin nhan - noi dung da duoc ma
 * hoa dau-cuoi thi khong nen hien ra man hinh khoa / trung tam thong bao.
 */
public final class DesktopNotifier {

    private static final Logger LOG = Logger.getLogger(DesktopNotifier.class.getName());
    private static TrayIcon trayIcon;
    private static volatile Runnable onClick = () -> { };

    private DesktopNotifier() {
    }

    /** Hanh dong khi nguoi dung bam vao thong bao (vd dua cua so len truoc). */
    public static void setOnClick(Runnable action) {
        onClick = action == null ? () -> { } : action;
    }

    public static void notify(String title, String text) {
        if (!isSupported()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            TrayIcon icon = ensureTrayIcon();
            if (icon != null) {
                icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
            }
        });
    }

    /** Go icon khoi khay khi thoat app (de luong AWT ket thuc, JVM thoat duoc). */
    public static void shutdown() {
        if (!isSupported()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
                trayIcon = null;
            }
        });
    }

    private static boolean isSupported() {
        try {
            return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static TrayIcon ensureTrayIcon() {
        if (trayIcon != null) {
            return trayIcon;
        }
        try {
            TrayIcon icon = new TrayIcon(createImage(), "Chat P2P Java");
            icon.setImageAutoSize(true);
            icon.addActionListener(event -> onClick.run());
            SystemTray.getSystemTray().add(icon);
            trayIcon = icon;
        } catch (AWTException | RuntimeException e) {
            LOG.fine(() -> "Khong tao duoc icon khay he thong: " + e.getMessage());
        }
        return trayIcon;
    }

    /** Icon tron mau nhan ve bang code - khong can file anh. */
    private static BufferedImage createImage() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2F81F7));
        g.fillOval(1, 1, size - 2, size - 2);
        g.setColor(Color.WHITE);
        g.fillRoundRect(8, 10, 16, 11, 6, 6);
        g.fillPolygon(new int[] {11, 15, 11}, new int[] {20, 20, 25}, 3);
        g.dispose();
        return image;
    }
}
