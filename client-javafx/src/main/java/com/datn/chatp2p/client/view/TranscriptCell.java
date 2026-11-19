package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.view.TranscriptEntry.NoticeKind;
import com.datn.chatp2p.client.view.TranscriptEntry.SystemNotice;
import com.datn.chatp2p.client.view.TranscriptEntry.UserMessage;
import com.datn.chatp2p.common.model.ChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Ve 1 dong trong khung chat - phong theo UX quen thuoc cua cac app chat:
 * tin cua minh ben phai (mau nhan), tin nguoi khac ben trai kem avatar; tin
 * lien tiep cung 1 nguoi trong vai phut duoc gop (an avatar/ten); dong he
 * thong nam giua.
 */
class TranscriptCell extends ListCell<TranscriptEntry> {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final long GROUPING_WINDOW_MILLIS = 3 * 60 * 1000;
    private static final double MAX_BUBBLE_WIDTH = 480;
    private static final double AVATAR_RADIUS = 15;

    private final String selfPeerId;
    private final Function<String, String> displayNameResolver;

    TranscriptCell(String selfPeerId, Function<String, String> displayNameResolver) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
        // De ListView tu co theo chieu rong, khong hien thanh cuon ngang khi tin dai
        setPrefWidth(0);
    }

    @Override
    protected void updateItem(TranscriptEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        setText(null);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        if (entry instanceof SystemNotice notice) {
            setGraphic(noticeRow(notice));
        } else if (entry instanceof UserMessage message) {
            setGraphic(messageRow(message.message(), isContinuation(message.message())));
        }
    }

    private Node noticeRow(SystemNotice notice) {
        Label label = new Label(notice.text(), new FontIcon(iconFor(notice.kind())));
        label.setWrapText(true);
        label.getStyleClass().addAll("system-notice", "notice-" + notice.kind().name().toLowerCase());
        HBox row = new HBox(label);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(6, 0, 6, 0));
        return row;
    }

    private Node messageRow(ChatMessage message, boolean continuation) {
        boolean mine = selfPeerId.equals(message.getAuthorId());

        Label textLabel = new Label(message.getText());
        textLabel.setWrapText(true);
        // Khong cho Label co lai thanh "..." - luon cao du de hien het cac dong
        textLabel.setMinHeight(Region.USE_PREF_SIZE);
        textLabel.getStyleClass().add("message-text");

        Label timeLabel = new Label(TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimeSent())));
        timeLabel.getStyleClass().add("message-time");
        if (mine) {
            timeLabel.setGraphic(new FontIcon(Feather.CHECK));
            timeLabel.setContentDisplay(ContentDisplay.RIGHT);
        }
        HBox timeRow = new HBox(timeLabel);
        timeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(2, textLabel, timeRow);
        bubble.setMaxWidth(MAX_BUBBLE_WIDTH);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");

        HBox row = new HBox(8);
        row.setPadding(new Insets(continuation ? 1 : 8, 0, 1, 0));
        if (mine) {
            row.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(bubble);
            return row;
        }

        Node avatarOrSpacer = continuation
                ? spacer(AVATAR_RADIUS * 2)
                : Avatars.create(displayNameResolver.apply(message.getAuthorId()), AVATAR_RADIUS);
        VBox column = new VBox(3);
        if (!continuation) {
            Label nameLabel = new Label(displayNameResolver.apply(message.getAuthorId()));
            nameLabel.getStyleClass().add("message-author");
            column.getChildren().add(nameLabel);
        }
        column.getChildren().add(bubble);
        row.setAlignment(Pos.TOP_LEFT);
        row.getChildren().addAll(avatarOrSpacer, column);
        return row;
    }

    /** Tin nay noi tiep tin ngay truoc cua cung 1 nguoi, trong vai phut -> gop lai. */
    private boolean isContinuation(ChatMessage message) {
        int index = getIndex();
        List<TranscriptEntry> items = getListView().getItems();
        if (index <= 0 || index > items.size() - 1) {
            return false;
        }
        return items.get(index - 1) instanceof UserMessage previous
                && previous.message().getAuthorId().equals(message.getAuthorId())
                && message.getTimeSent() - previous.message().getTimeSent() < GROUPING_WINDOW_MILLIS;
    }

    private static Region spacer(double width) {
        Region spacer = new Region();
        spacer.setMinWidth(width);
        spacer.setPrefWidth(width);
        return spacer;
    }

    private static Feather iconFor(NoticeKind kind) {
        return switch (kind) {
            case SECURE -> Feather.LOCK;
            case JOINED -> Feather.USER_PLUS;
            case LEFT -> Feather.USER_MINUS;
            case WARNING -> Feather.ALERT_TRIANGLE;
            case INFO -> Feather.INFO;
        };
    }
}
