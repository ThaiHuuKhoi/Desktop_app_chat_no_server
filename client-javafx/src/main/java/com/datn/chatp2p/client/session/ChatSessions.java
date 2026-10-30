package com.datn.chatp2p.client.session;

/** Chon cai dat {@link ChatSession} theo cau hinh nguoi dung chon o man hinh Home. */
public final class ChatSessions {

    private ChatSessions() {
    }

    public static ChatSession create(RoomConfig config) {
        if (config.demoMode()) {
            return new DemoChatSession();
        }
        return new P2pChatSession(config);
    }
}
