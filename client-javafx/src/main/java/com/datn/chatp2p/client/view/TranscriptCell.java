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
import javafx.scene.control.Tooltip;
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
    private final MessageContext messageContext;

    TranscriptCell(String selfPeerId, Function<String, String> displayNameResolver,
                   FileTransferActions fileActions, MessageContext messageContext) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
        this.fileActions = fileActions;
        this.messageContext = messageContext;
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
            setGraphic(messageRow(message.message(), isContinuation(message.message()), message.sharedBy()));
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
        Node preview = imagePreview(status);
        if (preview != null) {
            card.getChildren().add(preview);
        }
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

    private static final double PREVIEW_WIDTH = 300;
    private static final double PREVIEW_HEIGHT = 220;
    /** Anh thu nho da nap (o duoc ve lai rat thuong xuyen - khong doc lai file moi lan). */
    private static final java.util.Map<java.nio.file.Path, javafx.scene.image.Image> PREVIEW_CACHE =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<java.nio.file.Path, javafx.scene.image.Image> eldest) {
                    return size() > 40;
                }
            };

    /**
     * Anh thu nho cho file anh: ben nhan khi da nhan xong (file da kiem SHA-256), ben gui tu file goc.
     * Nap nen (khong lam dung giao dien); bam vao anh -&gt; mo bang trinh xem anh cua he dieu hanh.
     */
    private Node imagePreview(FileTransferStatus status) {
        java.nio.file.Path file = status.savedFile();
        boolean ready = status.direction() == Direction.OUTGOING || status.state() == State.COMPLETED;
        if (file == null || !ready || !MessageTexts.isImage(status.fileName()) || !java.nio.file.Files.isRegularFile(file)) {
            return null;
        }
        javafx.scene.image.Image image = PREVIEW_CACHE.computeIfAbsent(file, path -> new javafx.scene.image.Image(
                path.toUri().toString(), PREVIEW_WIDTH, PREVIEW_HEIGHT, true, true, true));
        if (image.isError()) {
            PREVIEW_CACHE.remove(file);
            return null;
        }
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(PREVIEW_WIDTH);
        view.setFitHeight(PREVIEW_HEIGHT);
        view.getStyleClass().add("file-preview");
        view.setStyle("-fx-cursor: hand;");
        Tooltip.install(view, new Tooltip("Bấm để mở ảnh"));
        view.setOnMouseClicked(event -> fileActions.open(status));
        return view;
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

    private Node messageRow(ChatMessage message, boolean continuation, String sharedBy) {
        boolean mine = selfPeerId.equals(message.getAuthorId());

        boolean deleted = messageContext.isDeleted(message.getId());
        // Markdown (dam/nghieng/code/link...) - MarkdownView tu dat min height = pref de khong bi cat.
        Node textLabel;
        if (deleted) {
            Label removed = new Label("Tin nhắn đã bị xoá", new FontIcon(Feather.SLASH));
            removed.getStyleClass().add("message-deleted");
            textLabel = removed;
        } else {
            textLabel = MarkdownView.render(message.getText());
        }
        textLabel.getStyleClass().add("message-text");

        Label timeLabel = new Label(TIME_FORMATTER.format(Instant.ofEpochMilli(message.getTimeSent()))
                + (!deleted && messageContext.isEdited(message.getId()) ? " · đã sửa" : ""));
        timeLabel.getStyleClass().add("message-time");
        if (mine) {
            DeliveryStatuses.Status delivery = messageContext.delivery(message.getId());
            Feather icon = Feather.CHECK;
            String tooltip = null;
            if (delivery != null) {
                switch (delivery.kind()) {
                    case SENDING -> {
                        icon = Feather.CLOCK;
                        tooltip = "Đang chờ người nhận xác nhận";
                    }
                    case DELIVERED -> {
                        icon = Feather.CHECK_CIRCLE;
                        tooltip = "Máy người nhận đã xác nhận nhận được tin";
                    }
                    case PARTIAL -> {
                        icon = Feather.CHECK;
                        tooltip = delivery.delivered() + " trên " + delivery.recipients() + " người đã nhận";
                    }
                    case FAILED -> {
                        icon = Feather.ALERT_CIRCLE;
                        tooltip = "Không nhận được xác nhận (người nhận rời phòng hoặc mất kết nối)";
                    }
                    case NO_RECIPIENT -> tooltip = "Phòng chưa có ai, tin chỉ lưu vào lịch sử";
                    case READ, PARTIALLY_READ -> {
                        icon = Feather.EYE;
                        tooltip = delivery.read() + " trên " + delivery.recipients() + " người đã xem";
                    }
                }
                String label = delivery.label();
                if (!label.isEmpty()) {
                    timeLabel.setText(timeLabel.getText() + " · " + label);
                }
                if (delivery.kind() == DeliveryStatuses.Status.Kind.FAILED) {
                    timeLabel.getStyleClass().add("message-time-failed");
                }
            }
            timeLabel.setGraphic(new FontIcon(icon));
            timeLabel.setContentDisplay(ContentDisplay.RIGHT);
            if (tooltip != null) {
                timeLabel.setTooltip(new Tooltip(tooltip));
            }
        }
        HBox timeRow = new HBox(6, timeLabel);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        if (UserMessage.SAVED_ON_DEVICE.equals(sharedBy)) {
            Label savedLabel = new Label("đã lưu trên máy", new FontIcon(Feather.HARD_DRIVE));
            savedLabel.getStyleClass().add("message-history");
            savedLabel.setTooltip(new Tooltip("Tin nhắn từ lần vào phòng trước, lưu (mã hoá) trên máy bạn"));
            timeRow.getChildren().add(0, savedLabel);
        } else if (sharedBy != null) {
            Label historyLabel = new Label("lịch sử · " + sharedBy + " chia sẻ", new FontIcon(Feather.CLOCK));
            historyLabel.getStyleClass().add("message-history");
            historyLabel.setTooltip(new Tooltip("Tin nhắn trước khi bạn vào phòng, do " + sharedBy
                    + " gửi lại. Không xác minh được người viết từng tin."));
            timeRow.getChildren().add(0, historyLabel);
        }

        VBox bubble = new VBox(2, textLabel, timeRow);
        Node quote = deleted ? null : replyQuote(message);
        if (quote != null) {
            bubble.getChildren().add(0, quote);
        }
        bubble.setMaxWidth(MAX_BUBBLE_WIDTH);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");
        if (!deleted) {
            installMessageMenu(bubble, message, mine);
        }
        Node reactions = deleted ? null : reactionRow(message, mine);

        HBox row = new HBox(8);
        row.setPadding(new Insets(continuation ? 1 : 8, 0, 1, 0));
        // Re chuot vao tin -> dai 5 cam xuc hien CANH bong bong, bam 1 lan la tha. Luon chiem cho
        // (chi an / hien) nen danh sach khong bi giat khi re chuot qua cac tin.
        Node bubbleLine = bubble;
        if (!deleted) {
            HBox quick = quickReactions(message);
            quick.visibleProperty().bind(row.hoverProperty());
            HBox line = mine ? new HBox(6, quick, bubble) : new HBox(6, bubble, quick);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setFillHeight(false); // dai cam xuc giu chieu cao vua du, khong keo theo bong bong
            bubbleLine = line;
        }
        if (mine) {
            row.setAlignment(Pos.CENTER_RIGHT);
            if (reactions == null) {
                row.getChildren().add(bubbleLine);
            } else {
                VBox stack = new VBox(2, bubbleLine, reactions);
                stack.setAlignment(Pos.CENTER_RIGHT);
                row.getChildren().add(stack);
            }
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
        column.getChildren().add(bubbleLine);
        if (reactions != null) {
            column.getChildren().add(reactions);
        }
        row.setAlignment(Pos.TOP_LEFT);
        row.getChildren().addAll(avatarOrSpacer, column);
        return row;
    }

    /** Khung trich dan tin dang duoc tra loi (ten + doan dau noi dung), hoac null. */
    private Node replyQuote(ChatMessage message) {
        if (message.getReplyToId() == null) {
            return null;
        }
        ChatMessage original = messageContext.findMessage(message.getReplyToId());
        String author = original == null ? "" : displayNameResolver.apply(original.getAuthorId());
        String snippet = original == null ? "(tin nhắn không còn trong phiên này)" : MessageTexts.snippet(original.getText());
        Label who = new Label(author.isEmpty() ? "Trả lời" : "Trả lời " + author, new FontIcon(Feather.CORNER_UP_LEFT));
        who.getStyleClass().add("reply-quote-author");
        Label text = new Label(snippet);
        text.getStyleClass().add("reply-quote-text");
        text.setWrapText(true);
        VBox box = new VBox(1, who, text);
        box.getStyleClass().add("reply-quote");
        return box;
    }


    /**
     * Cac "chip" cam xuc duoi bong bong (anh mau + so nguoi): bam de bat / tat cam xuc cua minh, re
     * chuot de xem ai da tha. Tin chua co cam xuc -&gt; null.
     */
    private Node reactionRow(ChatMessage message, boolean mine) {
        java.util.Map<String, Integer> counts = messageContext.reactions(message.getId());
        if (counts.isEmpty()) {
            return null;
        }
        HBox chips = new HBox(4);
        chips.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        counts.forEach((emoji, count) -> {
            boolean own = messageContext.reactedBySelf(message.getId(), emoji);
            Label chip = new Label(String.valueOf(count), EmojiImages.node(emoji, 16));
            chip.setGraphicTextGap(4);
            chip.getStyleClass().add("reaction-chip");
            if (own) {
                chip.getStyleClass().add("reaction-chip-mine");
            }
            String names = messageContext.reactorNames(message.getId(), emoji);
            String shown = MessageTexts.displayEmoji(emoji);
            chip.setTooltip(new Tooltip((names.isEmpty() ? shown : shown + " " + names)
                    + (own ? "\nBấm để bỏ cảm xúc của bạn" : "\nBấm để thả cùng")));
            chip.setStyle("-fx-cursor: hand;");
            chip.setOnMouseClicked(event -> messageContext.toggleReaction(message, emoji));
            chips.getChildren().add(chip);
        });
        return chips;
    }

    /** Dai 5 cam xuc (anh mau) canh bong bong: bam 1 lan la tha, bam lai cam xuc da tha la bo. */
    private HBox quickReactions(ChatMessage message) {
        HBox bar = new HBox(2);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().add("quick-reactions");
        for (String emoji : com.datn.chatp2p.common.protocol.ReactionPayload.ALLOWED) {
            boolean own = messageContext.reactedBySelf(message.getId(), emoji);
            Button pick = new Button(null, EmojiImages.node(emoji, 20));
            pick.getStyleClass().add("reaction-pick");
            if (own) {
                pick.getStyleClass().add("reaction-pick-mine");
            }
            pick.setFocusTraversable(false);
            pick.setTooltip(new Tooltip(own ? "Bỏ cảm xúc này" : "Thả cảm xúc"));
            pick.setOnAction(event -> messageContext.toggleReaction(message, emoji));
            bar.getChildren().add(pick);
        }
        return bar;
    }

    /** Chuot phai vao bong bong: Tra loi, sao chep, sua / xoa tin cua minh. */
    private void installMessageMenu(Node bubble, ChatMessage message, boolean mine) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem reply = new javafx.scene.control.MenuItem("Trả lời", new FontIcon(Feather.CORNER_UP_LEFT));
        reply.setOnAction(event -> messageContext.reply(message));
        javafx.scene.control.MenuItem copy = new javafx.scene.control.MenuItem("Sao chép", new FontIcon(Feather.COPY));
        copy.setOnAction(event -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(message.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        menu.getItems().addAll(reply, copy);
        // Chi tin cua minh, va khong phai tin lay tu lich su nguoi khac chia se.
        if (mine && getItem() instanceof UserMessage user && !user.fromHistory()) {
            javafx.scene.control.MenuItem edit = new javafx.scene.control.MenuItem("Sửa", new FontIcon(Feather.EDIT_2));
            edit.setOnAction(event -> messageContext.edit(message));
            javafx.scene.control.MenuItem delete = new javafx.scene.control.MenuItem("Xoá", new FontIcon(Feather.TRASH_2));
            delete.setOnAction(event -> messageContext.delete(message));
            menu.getItems().addAll(edit, delete);
        }
        bubble.setOnContextMenuRequested(event -> menu.show(bubble, event.getScreenX(), event.getScreenY()));
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
