package com.datn.chatp2p.client.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Emoji mau cho cam xuc. JavaFX tren Windows chi ve emoji DON SAC (da thu 21, 22, 23, 24), nen 5 cam
 * xuc dung anh Twemoji (https://github.com/jdecked/twemoji, do hoa CC-BY 4.0) trong
 * {@code /emoji/<ma-unicode>.png}. Khong co anh -&gt; ve chu nhu cu.
 */
final class EmojiImages {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private EmojiImages() {
    }

    /** Ten file anh: ma Unicode dang hex, bo U+FE0F, noi bang '-' (vd "❤️" -&gt; "2764"). */
    static String fileName(String emoji) {
        return emoji.codePoints()
                .filter(cp -> cp != 0xFE0F)
                .mapToObj(Integer::toHexString)
                .collect(Collectors.joining("-")) + ".png";
    }

    /** Anh mau co kich thuoc {@code size} px, hoac null neu khong co anh cho emoji nay. */
    static ImageView image(String emoji, double size) {
        Image image = CACHE.computeIfAbsent(emoji, EmojiImages::load);
        if (image == null) {
            return null;
        }
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    /** Anh mau, hoac Label chu neu khong co anh. */
    static Node node(String emoji, double size) {
        ImageView view = image(emoji, size);
        if (view != null) {
            return view;
        }
        Label text = new Label(MessageTexts.displayEmoji(emoji));
        text.setStyle("-fx-font-size: " + Math.round(size * 0.9) + "px;");
        return text;
    }

    private static Image load(String emoji) {
        try (InputStream in = EmojiImages.class.getResourceAsStream("/emoji/" + fileName(emoji))) {
            return in == null ? null : new Image(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
