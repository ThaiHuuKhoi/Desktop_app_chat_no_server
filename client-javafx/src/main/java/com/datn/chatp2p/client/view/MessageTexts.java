package com.datn.chatp2p.client.view;

import java.util.Locale;
import java.util.Set;

/** Xu ly chu cho o tin nhan - khong dung JavaFX de test duoc. */
final class MessageTexts {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final int SNIPPET_LENGTH = 80;

    private MessageTexts() {
    }

    /** Doan dau 1 dong cua tin (dung cho trich dan khi tra loi). */
    static String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "…";
    }

    /** File anh hien duoc xem truoc (theo duoi file). */
    static boolean isImage(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
