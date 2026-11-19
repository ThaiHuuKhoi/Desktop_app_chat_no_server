package com.datn.chatp2p.client.view;

import atlantafx.base.theme.Styles;
import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.client.session.FileTransferStatus.Direction;
import com.datn.chatp2p.client.session.FileTransferStatus.State;
import com.datn.chatp2p.client.view.TranscriptEntry.FileTransfer;
import com.datn.chatp2p.client.view.TranscriptEntry.NoticeKind;
import com.datn.chatp2p.client.view.TranscriptEntry.SystemNotice;
import com.datn.chatp2p.client.view.TranscriptEntry.UserMessage;
import com.datn.chatp2p.common.model.ChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
    private final FileTransferActions fileActions;

    TranscriptCell(String selfPeerId, Function<String, String> displayNameResolver,
                   FileTransferActions fileActions) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
        this.fileActions = fileActions;
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
        } else if (entry instanceof FileTransfer file) {
            setGraphic(fileRow(file.status()));
        }
    }

    /** The file: icon + ten + dung luong, dong trang thai, thanh tien do, cac nut. */
    private Node fileRow(FileTransferStatus status) {
        boolean mine = status.direction() == Direction.OUTGOING;
        String who = displayNameResolver.apply(status.peerId());

        FontIcon fileIcon = new FontIcon(iconFor(status));
        StackPane iconBox = new StackPane(fileIcon);
        iconBox.getStyleClass().addAll("file-icon-box", "file-icon-" + tone(status));

        Label nameLabel = new Label(status.fileName());
        nameLabel.getStyleClass().add("file-name");
        nameLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        nameLabel.setMaxWidth(300);

        Label sizeLabel = new Label(FileTransferTexts.size(status.fileSize()));
        sizeLabel.getStyleClass().add("file-size");

        VBox titleColumn = new VBox(1, nameLabel, sizeLabel);
        titleColumn.setMinWidth(0);
        HBox.setHgrow(titleColumn, Priority.ALWAYS);
        HBox header = new HBox(10, iconBox, titleColumn);
        header.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label(FileTransferTexts.status(status, who));
        statusLabel.setWrapText(true);
        statusLabel.setMinHeight(Region.USE_PREF_SIZE);
        statusLabel.getStyleClass().addAll("file-status", "file-status-" + tone(status));

        VBox card = new VBox(8, header);
        card.getStyleClass().add("file-card");
        card.setMaxWidth(MAX_BUBBLE_WIDTH - 80);
        card.setPrefWidth(340);

        if (status.state() == State.IN_PROGRESS || status.state() == State.VERIFYING) {
            ProgressBar progressBar = new ProgressBar(
                    status.state() == State.VERIFYING ? ProgressBar.INDETERMINATE_PROGRESS : status.progress());
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.getStyleClass().addAll("file-progress", Styles.SMALL);
            card.getChildren().add(progressBar);
        }
        card.getChildren().add(statusLabel);

        HBox buttons = fileButtons(status);
        if (!buttons.getChildren().isEmpty()) {
            card.getChildren().add(buttons);
        }

        HBox row = new HBox(8);
        row.setPadding(new Insets(8, 0, 2, 0));
        if (mine) {
            row.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(card);
            return row;
        }
        Label nameOfSender = new Label(who);
        nameOfSender.getStyleClass().add("message-author");
        VBox column = new VBox(3, nameOfSender, card);
        row.setAlignment(Pos.TOP_LEFT);
        row.getChildren().addAll(Avatars.create(who, AVATAR_RADIUS), column);
        return row;
    }

    private HBox fileButtons(FileTransferStatus status) {
        HBox buttons = new HBox(6);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        boolean incoming = status.direction() == Direction.INCOMING;
        switch (status.state()) {
            case OFFERED -> buttons.getChildren().addAll(
                    fileButton("Từ chối", Feather.X, false, () -> fileActions.reject(status)),
                    fileButton("Nhận", Feather.DOWNLOAD, true, () -> fileActions.accept(status)));
            case WAITING_ANSWER, IN_PROGRESS -> buttons.getChildren().add(
                    fileButton("Huỷ", Feather.X, false, () -> fileActions.cancel(status)));
            case COMPLETED -> {
                if (incoming && status.savedFile() != null) {
                    buttons.getChildren().add(
                            fileButton("Mở thư mục", Feather.FOLDER, false, () -> fileActions.showInFolder(status)));
                }
            }
            default -> {
                // trang thai cuoi khac: khong co nut
            }
        }
        return buttons;
    }

    private static Button fileButton(String text, Feather icon, boolean primary, Runnable action) {
        Button button = new Button(text, new FontIcon(icon));
        button.getStyleClass().add(Styles.SMALL);
        button.getStyleClass().add(primary ? Styles.ACCENT : Styles.FLAT);
        button.setOnAction(event -> {
            button.setDisable(true); // tranh bam 2 lan truoc khi trang thai cap nhat
            action.run();
        });
        return button;
    }

    private static Feather iconFor(FileTransferStatus status) {
        return switch (status.state()) {
            case COMPLETED -> Feather.CHECK_CIRCLE;
            case FAILED, REJECTED, CANCELLED -> Feather.X_CIRCLE;
            case OFFERED -> Feather.DOWNLOAD;
            default -> Feather.FILE;
        };
    }

    /** Mau cua the: thanh cong / loi / binh thuong. */
    private static String tone(FileTransferStatus status) {
        return switch (status.state()) {
            case COMPLETED -> "success";
            case FAILED -> "danger";
            case REJECTED, CANCELLED -> "muted";
            default -> "accent";
        };
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
