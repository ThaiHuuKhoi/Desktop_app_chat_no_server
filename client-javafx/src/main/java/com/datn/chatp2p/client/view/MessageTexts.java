package com.datn.chatp2p.client.view;

import java.util.Locale;
import java.util.Set;

/** Xu ly chu cho o tin nhan - khong dung JavaFX de test duoc. */
final class MessageTexts {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final int SNIPPET_LENGTH = 80;

    private MessageTexts() {
    }

    /** Doan dau 1 dong cua tin, bo ky hieu Markdown don gian (dung cho trich dan khi tra loi). */
    static String snippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip()
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("~~(.+?)~~", "$1")
                .replaceAll("`(.+?)`", "$1")
                .replaceAll("(?<![\\w*])\\*([^*\\s][^*]*?)\\*(?![\\w*])", "$1");
        return oneLine.length() <= SNIPPET_LENGTH ? oneLine : oneLine.substring(0, SNIPPET_LENGTH) + "…";
    }

    /**
     * Emoji de hien tren JavaFX: bo ky tu bien the U+FE0F (JavaFX tren Windows ve no thanh o vuong,
     * vd "❤️" -&gt; "❤▯"). Chi dung khi hien thi - du lieu gui qua mang giu nguyen.
     */
    static String displayEmoji(String emoji) {
        return emoji.replace("️", "");
    }

    /** "A", "A và B", "A, B và C", "A, B, C và 2 người khác" - dung cho tooltip cam xuc. */
    static String joinNames(java.util.List<String> names) {
        int shown = Math.min(names.size(), 3);
        if (names.size() > 4) {
            return String.join(", ", names.subList(0, shown)) + " và " + (names.size() - shown) + " người khác";
        }
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " và " + names.get(names.size() - 1);
    }

    /** File anh hien duoc xem truoc (theo duoi file). */
    static boolean isImage(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
