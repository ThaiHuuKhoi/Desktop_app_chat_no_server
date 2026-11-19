package com.datn.chatp2p.client.util;

import javafx.application.HostServices;

import java.net.URI;
import java.util.Locale;

/** Mo link trong tin nhan bang trinh duyet mac dinh - CHI http/https (khong file:, javascript:...). */
public final class LinkOpener {

    private static HostServices hostServices;

    private LinkOpener() {
    }

    /** Goi 1 lan luc khoi dong ({@code Application.getHostServices()}). */
    public static void init(HostServices services) {
        hostServices = services;
    }

    public static boolean isSafe(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return scheme != null && (scheme.toLowerCase(Locale.ROOT).equals("http")
                    || scheme.toLowerCase(Locale.ROOT).equals("https"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static void open(String url) {
        if (hostServices != null && isSafe(url)) {
            hostServices.showDocument(url);
        }
    }
}
