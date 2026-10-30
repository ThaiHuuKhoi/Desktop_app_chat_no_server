package com.datn.chatp2p.client.session;

/** Tao session mang that cho phong chat. */
public final class ChatSessions {

    private ChatSessions() {
    }

    public static ChatSession create(RoomConfig config) {
        return new P2pChatSession(config);
    }
}
