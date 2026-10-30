package com.datn.chatp2p.client.session;

/** Chon cai dat {@link ChatSession} theo cau hinh nguoi dung chon o man hinh Home. */
public final class ChatSessions {

    private ChatSessions() {
    }

    /**
     * @throws UnsupportedOperationException neu chon che do mang that ma chua
     *         tich hop {@code RoomSession} - thong bao da viet cho nguoi dung doc.
     */
    public static ChatSession create(RoomConfig config) {
        if (config.demoMode()) {
            return new DemoChatSession();
        }
        // TODO (tich hop voi p2p-core): return new P2pChatSession(config);
        throw new UnsupportedOperationException(
                "Chế độ mạng thật chưa được tích hợp (RoomSession của p2p-core). Hãy bật \"Chế độ demo\".");
    }
}
