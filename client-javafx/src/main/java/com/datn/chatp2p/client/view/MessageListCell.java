package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.model.ChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

/**
 * Mot bong bong tin nhan trong khung chat: can phai neu la tin cua chinh
 * minh, can trai neu la tin cua doi phuong - phong theo UX cua
 * {@code components/Message} trong chitchatter.
 */
class MessageListCell extends ListCell<ChatMessage> {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final String myPeerId;
    private final Function<String, String> displayNameResolver;

    MessageListCell(String myPeerId, Function<String, String> displayNameResolver) {
        this.myPeerId = myPeerId;
        this.displayNameResolver = displayNameResolver;
    }

    @Override
    protected void updateItem(ChatMessage message, boolean empty) {
        super.updateItem(message, empty);
        if (empty || message == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        boolean isMine = myPeerId.equals(message.getAuthorId());

        Label metaLabel = new Label(displayNameResolver.apply(message.getAuthorId())
                + " * " + TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimeSent())));
        metaLabel.getStyleClass().add("message-meta");

        Label textLabel = new Label(message.getText());
        textLabel.setWrapText(true);
        textLabel.getStyleClass().add("message-text");

        VBox bubble = new VBox(2, metaLabel, textLabel);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setMaxWidth(420);
        bubble.getStyleClass().add(isMine ? "message-bubble-mine" : "message-bubble-other");

        HBox row = new HBox(bubble);
        row.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        setGraphic(row);
        setText(null);
    }

    static Function<String, String> nameResolver(Map<String, String> peerIdToName) {
        return peerId -> peerIdToName.getOrDefault(peerId, "???");
    }
}
