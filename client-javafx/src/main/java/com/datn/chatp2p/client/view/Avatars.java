package com.datn.chatp2p.client.view;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

/** Avatar tron chu cai dau, mau co dinh theo ten (cung 1 nguoi luon cung 1 mau). */
final class Avatars {

    private static final String[] PALETTE = {
            "#2f81f7", "#8957e5", "#d29922", "#2da44e", "#db6d28", "#bf3989", "#0969da", "#1a7f37"
    };

    private Avatars() {
    }

    static StackPane create(String name, double radius) {
        Circle circle = new Circle(radius);
        circle.setStyle("-fx-fill: " + colorFor(name) + ";");
        Text initial = new Text(initialOf(name));
        initial.getStyleClass().add("avatar-initial");
        initial.setStyle("-fx-font-size: " + Math.round(radius * 0.9) + "px;");
        StackPane avatar = new StackPane(circle, initial);
        avatar.setMinSize(radius * 2, radius * 2);
        avatar.setMaxSize(radius * 2, radius * 2);
        return avatar;
    }

    static String initialOf(String name) {
        return name == null || name.isBlank() ? "?" : name.strip().substring(0, 1).toUpperCase();
    }

    private static String colorFor(String name) {
        int hash = name == null ? 0 : name.hashCode();
        return PALETTE[Math.floorMod(hash, PALETTE.length)];
    }
}
