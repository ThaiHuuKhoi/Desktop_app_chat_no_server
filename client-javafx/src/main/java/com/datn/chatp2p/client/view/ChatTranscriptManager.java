package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.client.view.TranscriptEntry.FileTransfer;
import com.datn.chatp2p.common.model.ChatMessage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Owns group and direct-message transcript views and their tab state. */
final class ChatTranscriptManager {

    private final String selfPeerId;
    private final Function<String, String> displayNameResolver;
    private final FileTransferActions fileActions;
    private final MessageContext messageContext;
    private ListView<TranscriptEntry> groupView;
    private final ObservableList<TranscriptEntry> groupTranscript = FXCollections.observableArrayList();
    private final Map<String, ObservableList<TranscriptEntry>> directTranscripts = new HashMap<>();
    private final Map<String, ListView<TranscriptEntry>> directViews = new HashMap<>();

    private TabPane tabs;

    ChatTranscriptManager(String selfPeerId, Function<String, String> displayNameResolver,
                          FileTransferActions fileActions, MessageContext messageContext) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
        this.fileActions = fileActions;
        this.messageContext = messageContext;
    }

    /** Tim tin theo id trong khung nhom va cac tab rieng (de hien trich dan khi tra loi), hoac null. */
    ChatMessage findMessage(String messageId) {
        java.util.stream.Stream<TranscriptEntry> all = java.util.stream.Stream.concat(
                groupTranscript.stream(), directTranscripts.values().stream().flatMap(java.util.List::stream));
        return all.filter(entry -> entry instanceof TranscriptEntry.UserMessage user
                        && user.message().getId().equals(messageId))
                .map(entry -> ((TranscriptEntry.UserMessage) entry).message())
                .findFirst().orElse(null);
    }

    /** Thay noi dung tin {@code updated.getId()} o moi khung (sau khi tac gia sua / xoa). */
    void replaceMessage(ChatMessage updated) {
        java.util.List<ObservableList<TranscriptEntry>> lists = new java.util.ArrayList<>(directTranscripts.values());
        lists.add(groupTranscript);
        for (ObservableList<TranscriptEntry> list : lists) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof TranscriptEntry.UserMessage user && user.message().getId().equals(updated.getId())) {
                    list.set(i, new TranscriptEntry.UserMessage(updated, user.sharedBy()));
                }
            }
        }
    }

    /** Ve lai moi khung chat (vd khi trang thai "da nhan" cua 1 tin thay doi). */
    void refreshAll() {
        if (groupView != null) {
            groupView.refresh();
        }
        directViews.values().forEach(ListView::refresh);
    }

    void configure(TabPane tabs, ListView<TranscriptEntry> groupView) {
        this.tabs = tabs;
        this.groupView = groupView;
        groupView.setItems(groupTranscript);
        groupView.setCellFactory(list -> newCell());
        groupTranscript.addListener((ListChangeListener<TranscriptEntry>) change -> {
            // Chi cuon xuong khi co dong MOI, khong cuon khi the file cap nhat tien do
            while (change.next()) {
                if (change.wasAdded() && !change.wasReplaced()) {
                    scrollToEnd(groupView);
                    return;
                }
            }
        });
    }

    /**
     * Cuon xuong tin cuoi SAU lan layout ke tiep: o Markdown co chieu cao khac nhau, goi
     * scrollTo ngay khi vua them thi ListView chua do xong o moi nen tin cuoi bi cat.
     */
    private static void scrollToEnd(ListView<TranscriptEntry> view) {
        view.scrollTo(view.getItems().size() - 1);
        Platform.runLater(() -> view.scrollTo(view.getItems().size() - 1));
    }

    ObservableList<TranscriptEntry> groupTranscript() {
        return groupTranscript;
    }

    void addGroup(TranscriptEntry entry) {
        groupTranscript.add(entry);
    }

    /**
     * Chen lich su vao khung nhom theo thoi gian, bo qua tin da co (cung id).
     *
     * @return so tin thuc su duoc them.
     */
    int mergeGroupHistory(java.util.List<ChatMessage> messages, String sharedBy) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (TranscriptEntry entry : groupTranscript) {
            if (entry instanceof TranscriptEntry.UserMessage user) {
                existing.add(user.message().getId());
            }
        }
        int added = 0;
        for (ChatMessage message : messages.stream()
                .sorted(java.util.Comparator.comparingLong(ChatMessage::getTimeSent)).toList()) {
            if (!existing.add(message.getId())) {
                continue;
            }
            int index = groupTranscript.size();
            for (int i = 0; i < groupTranscript.size(); i++) {
                if (groupTranscript.get(i) instanceof TranscriptEntry.UserMessage user
                        && user.message().getTimeSent() > message.getTimeSent()) {
                    index = i;
                    break;
                }
            }
            groupTranscript.add(index, new TranscriptEntry.UserMessage(message, sharedBy));
            added++;
        }
        return added;
    }

    boolean hasGroupUserMessages() {
        return groupTranscript.stream().anyMatch(entry ->
                entry instanceof TranscriptEntry.UserMessage || entry instanceof FileTransfer);
    }

    void addDirect(String peerId, String displayName, ChatMessage message) {
        addDirect(peerId, displayName, message, true);
    }

    /** @param focus {@code false} = tao tab neu chua co nhung KHONG chuyen sang (nguoi dung dang go do). */
    void addDirect(String peerId, String displayName, ChatMessage message, boolean focus) {
        openDirect(peerId, displayName, focus);
        directTranscripts.get(peerId).add(new TranscriptEntry.UserMessage(message));
        directViews.get(peerId).refresh();
        scrollToEnd(directViews.get(peerId));
    }

    /**
     * Them the file moi, hoac thay the file cu cung {@code fileId} bang trang
     * thai moi. File gui trong DM nam o tab DM cua nguoi do.
     */
    void upsertFile(FileTransferStatus status, String peerDisplayName) {
        ObservableList<TranscriptEntry> target;
        if (status.direct()) {
            // Chi chuyen sang tab DM khi co loi moi / vua gui, khong phai moi lan cap nhat tien do
            boolean newTransfer = status.state() == FileTransferStatus.State.OFFERED
                    || status.state() == FileTransferStatus.State.WAITING_ANSWER;
            openDirect(status.peerId(), peerDisplayName, newTransfer);
            target = directTranscripts.get(status.peerId());
        } else {
            target = groupTranscript;
        }
        for (int i = target.size() - 1; i >= 0; i--) {
            if (target.get(i) instanceof FileTransfer existing
                    && existing.status().fileId().equals(status.fileId())) {
                target.set(i, new FileTransfer(status));
                return;
            }
        }
        target.add(new FileTransfer(status));
        if (status.direct()) {
            scrollToEnd(directViews.get(status.peerId()));
        }
    }

    void openDirect(String peerId, String displayName) {
        openDirect(peerId, displayName, true);
    }

    private void openDirect(String peerId, String displayName, boolean focus) {
        for (Tab tab : tabs.getTabs()) {
            if (peerId.equals(tab.getUserData())) {
                if (focus) {
                    tabs.getSelectionModel().select(tab);
                }
                return;
            }
        }
        ObservableList<TranscriptEntry> messages =
                directTranscripts.computeIfAbsent(peerId, ignored -> FXCollections.observableArrayList());
        ListView<TranscriptEntry> view = new ListView<>(messages);
        view.setStyle("-fx-background-color: transparent;");
        view.getStyleClass().add("transcript");
        view.setCellFactory(list -> newCell());
        directViews.put(peerId, view);
        Tab tab = new Tab("DM: " + displayName, view);
        tab.setUserData(peerId);
        tabs.getTabs().add(tab);
        if (focus) {
            tabs.getSelectionModel().select(tab);
        }
    }

    private TranscriptCell newCell() {
        return new TranscriptCell(selfPeerId, displayNameResolver, fileActions, messageContext);
    }

    String selectedDirectPeerId() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected == null || selected.getUserData() == null
                ? null
                : (String) selected.getUserData();
    }
}
