package com.datn.chatp2p.client.view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** 1 dong hoi thoai o thanh ben: bieu tuong nhom / avatar, ten, dong phu, so tin chua doc. */
class ConversationCell extends ListCell<ConversationItem> {

    ConversationCell() {
        setPrefWidth(0);
    }

    @Override
    protected void updateItem(ConversationItem item, boolean empty) {
        super.updateItem(item, empty);
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }
        Node icon;
        if (item.isGroup()) {
            StackPane box = new StackPane(new FontIcon(Feather.USERS));
            box.getStyleClass().add("conversation-group-icon");
            icon = box;
        } else {
            icon = Avatars.create(item.title(), 14);
        }

        Label title = new Label(item.title());
        title.getStyleClass().add("conversation-title");
        VBox text = new VBox(1, title);
        if (item.subtitle() != null && !item.subtitle().isEmpty()) {
            Label subtitle = new Label(item.subtitle());
            subtitle.getStyleClass().add("conversation-subtitle");
            text.getChildren().add(subtitle);
        }
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        if (item.unread() > 0) {
            Label badge = new Label(item.unread() > 99 ? "99+" : String.valueOf(item.unread()));
            badge.getStyleClass().add("unread-badge");
            row.getChildren().add(badge);
            title.getStyleClass().add("conversation-title-unread");
        }
        setGraphic(row);
    }
}
