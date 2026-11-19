package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.FileTransferStatus;
import com.datn.chatp2p.client.view.TranscriptEntry.FileTransfer;
import com.datn.chatp2p.common.model.ChatMessage;
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
    private final ObservableList<TranscriptEntry> groupTranscript = FXCollections.observableArrayList();
    private final Map<String, ObservableList<TranscriptEntry>> directTranscripts = new HashMap<>();
    private final Map<String, ListView<TranscriptEntry>> directViews = new HashMap<>();

    private TabPane tabs;

    ChatTranscriptManager(String selfPeerId, Function<String, String> displayNameResolver,
                          FileTransferActions fileActions) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
        this.fileActions = fileActions;
    }

    void configure(TabPane tabs, ListView<TranscriptEntry> groupView) {
        this.tabs = tabs;
        groupView.setItems(groupTranscript);
        groupView.setCellFactory(list -> newCell());
        groupTranscript.addListener((ListChangeListener<TranscriptEntry>) change -> {
            // Chi cuon xuong khi co dong MOI, khong cuon khi the file cap nhat tien do
            while (change.next()) {
                if (change.wasAdded() && !change.wasReplaced()) {
                    groupView.scrollTo(groupTranscript.size() - 1);
                    return;
                }
            }
        });
    }

    ObservableList<TranscriptEntry> groupTranscript() {
        return groupTranscript;
    }

    void addGroup(TranscriptEntry entry) {
        groupTranscript.add(entry);
    }

    boolean hasGroupUserMessages() {
        return groupTranscript.stream().anyMatch(entry ->
                entry instanceof TranscriptEntry.UserMessage || entry instanceof FileTransfer);
    }

    void addDirect(String peerId, String displayName, ChatMessage message) {
        openDirect(peerId, displayName);
        directTranscripts.get(peerId).add(new TranscriptEntry.UserMessage(message));
        directViews.get(peerId).refresh();
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
            ensureDirect(status.peerId(), peerDisplayName, newTransfer);
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
            ListView<TranscriptEntry> view = directViews.get(status.peerId());
            view.scrollTo(target.size() - 1);
        }
    }

    void openDirect(String peerId, String displayName) {
        ensureDirect(peerId, displayName, true);
    }

    private void ensureDirect(String peerId, String displayName, boolean select) {
        for (Tab tab : tabs.getTabs()) {
            if (peerId.equals(tab.getUserData())) {
                if (select) {
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
        if (select) {
            tabs.getSelectionModel().select(tab);
        }
    }

    private TranscriptCell newCell() {
        return new TranscriptCell(selfPeerId, displayNameResolver, fileActions);
    }

    String selectedDirectPeerId() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected == null || selected.getUserData() == null
                ? null
                : (String) selected.getUserData();
    }
}
