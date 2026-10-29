package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DemoChatSessionTest {

    @Test
    void demoPeerJoinsThenRepliesThroughEncryptedChannel() throws Exception {
        CompletableFuture<SessionPeer> joined = new CompletableFuture<>();
        CompletableFuture<ChatMessage> reply = new CompletableFuture<>();
        DemoChatSession session = new DemoChatSession();
        session.start(new ChatSessionListener() {
            @Override
            public void onPeerJoined(SessionPeer peer) {
                joined.complete(peer);
            }

            @Override
            public void onPeerLeft(String peerId) {
            }

            @Override
            public void onMessageReceived(ChatMessage message) {
                reply.complete(message);
            }

            @Override
            public void onPeerConnectionFailed(String peerId, String reason) {
            }
        });

        try {
            SessionPeer peer = joined.get(5, TimeUnit.SECONDS);
            assertEquals(DemoChatSession.DEMO_PEER_NAME, peer.displayName());
            assertEquals(SessionPeer.ConnectionKind.DEMO, peer.connectionKind());
            assertNotNull(peer.localFingerprint());
            assertNotEquals(peer.localFingerprint(), peer.remoteFingerprint(),
                    "Van tay 2 ben phai la cua 2 khoa khac nhau");

            session.sendGroupMessage(new ChatMessage(session.selfPeerId(), "xin chào"));

            ChatMessage received = reply.get(5, TimeUnit.SECONDS);
            assertEquals(peer.peerId(), received.getAuthorId());
            assertFalse(received.getText().isBlank());
        } finally {
            session.leave();
        }
    }

    @Test
    void sendingBeforeAnyoneJoinedIsANoOpNotAnError() {
        DemoChatSession session = new DemoChatSession();
        try {
            session.sendGroupMessage(new ChatMessage(session.selfPeerId(), "chưa có ai"));
        } finally {
            session.leave();
        }
    }
}
