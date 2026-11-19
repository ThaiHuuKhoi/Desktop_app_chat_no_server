package com.datn.chatp2p.client.view;

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
    private final ObservableList<TranscriptEntry> groupTranscript = FXCollections.observableArrayList();
    private final Map<String, ObservableList<TranscriptEntry>> directTranscripts = new HashMap<>();
    private final Map<String, ListView<TranscriptEntry>> directViews = new HashMap<>();

    private TabPane tabs;

    ChatTranscriptManager(String selfPeerId, Function<String, String> displayNameResolver) {
        this.selfPeerId = selfPeerId;
        this.displayNameResolver = displayNameResolver;
    }

    void configure(TabPane tabs, ListView<TranscriptEntry> groupView) {
        this.tabs = tabs;
        groupView.setItems(groupTranscript);
        groupView.setCellFactory(list -> new TranscriptCell(selfPeerId, displayNameResolver));
        groupTranscript.addListener((ListChangeListener<TranscriptEntry>) change ->
                groupView.scrollTo(groupTranscript.size() - 1));
    }

    ObservableList<TranscriptEntry> groupTranscript() {
        return groupTranscript;
    }

    void addGroup(TranscriptEntry entry) {
        groupTranscript.add(entry);
    }

    boolean hasGroupUserMessages() {
        return groupTranscript.stream().anyMatch(entry -> entry instanceof TranscriptEntry.UserMessage);
    }

    void addDirect(String peerId, String displayName, ChatMessage message) {
        openDirect(peerId, displayName);
        directTranscripts.get(peerId).add(new TranscriptEntry.UserMessage(message));
        directViews.get(peerId).refresh();
    }

    void openDirect(String peerId, String displayName) {
        for (Tab tab : tabs.getTabs()) {
            if (peerId.equals(tab.getUserData())) {
                tabs.getSelectionModel().select(tab);
                return;
            }
        }
        ObservableList<TranscriptEntry> messages =
                directTranscripts.computeIfAbsent(peerId, ignored -> FXCollections.observableArrayList());
        ListView<TranscriptEntry> view = new ListView<>(messages);
        view.setStyle("-fx-background-color: transparent;");
        view.setCellFactory(list -> new TranscriptCell(selfPeerId, displayNameResolver));
        directViews.put(peerId, view);
        Tab tab = new Tab("DM: " + displayName, view);
        tab.setUserData(peerId);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    String selectedDirectPeerId() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return selected == null || selected.getUserData() == null
                ? null
                : (String) selected.getUserData();
    }
}
