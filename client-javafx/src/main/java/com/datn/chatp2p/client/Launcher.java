package com.datn.chatp2p.client;

/**
 * Diem vao cho ban dong goi (jpackage, jar thuong): lop main KHONG ke thua {@code Application},
 * vi JavaFX chay tu classpath se bao "JavaFX runtime components are missing" neu lop main la
 * {@code Application}. Chi chuyen tiep sang {@link ChatApplication}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ChatApplication.main(args);
    }
}
