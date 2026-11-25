package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessageEditPayload;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lenh sua / xoa toi TRUOC tin goc (UDP dao thu tu). Goi thang 2 ham nhan cua P2pChatSession (khong mo
 * mang) theo thu tu dao, kiem tra giao dien va lich su nhom nhan dung ket qua cuoi.
 */
class EarlyEditOrderingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private P2pChatSession session;
    private final List<ChatMessage> shown = new ArrayList<>();
    private final List<String> edits = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        session = new P2pChatSession(new RoomConfig("binh", "phong-test-" + UUID.randomUUID(), "ws://127.0.0.1:1", "", ""));
        ChatSessionListener listener = new ChatSessionListener() {
            @Override
            public void onPeerJoined(SessionPeer peer) {
            }

            @Override
            public void onPeerLeft(String peerId) {
            }

            @Override
            public void onMessageReceived(ChatMessage message) {
                shown.add(message);
            }

            @Override
            public void onDirectMessageReceived(String peerId, ChatMessage message) {
                shown.add(message);
            }

            @Override
            public void onMessageEdited(String messageId, String peerId, String newText) {
                edits.add(peerId + "/" + messageId + "=" + newText);
            }

            @Override
            public void onPeerConnectionFailed(String peerId, String reason) {
            }
        };
        Field field = P2pChatSession.class.getDeclaredField("listener");
        field.setAccessible(true);
        field.set(session, listener);
    }

    @Test
    void deleteArrivingBeforeTheMessageHidesItAndKeepsItOutOfHistory() throws Exception {
        receiveEdit("an", null, "m1", null, true);
        receiveMessage("an", null, "m1", "gui nham so tai khoan");

        assertEquals(1, shown.size());
        assertEquals("", shown.get(0).getText(), "Noi dung da xoa khong bao gio toi giao dien");
        assertEquals("an/m1=null", edits.get(edits.size() - 1), "Giao dien danh dau 'da xoa' nhu may khac");
        assertTrue(history().snapshot().isEmpty(), "Tin da xoa khong vao lich su nhom chia cho nguoi vao sau");
    }

    @Test
    void editArrivingBeforeTheMessageShowsTheNewText() throws Exception {
        receiveEdit("an", null, "m1", "8 gio", false);
        receiveMessage("an", null, "m1", "7 gio");

        assertEquals("8 gio", shown.get(0).getText());
        assertEquals("an/m1=8 gio", edits.get(edits.size() - 1));
        assertEquals("8 gio", history().snapshot().get(0).text());
    }

    @Test
    void earlyDeleteInDirectChatAlsoWorks() throws Exception {
        receiveEdit("an", EnvelopeNamespace.DIRECT_MESSAGE, "d1", null, true);
        receiveMessage("an", EnvelopeNamespace.DIRECT_MESSAGE, "d1", "bi mat");

        assertEquals("", shown.get(0).getText());
    }

    @Test
    void someoneElseCannotDeleteAMessageAheadOfTime() throws Exception {
        receiveEdit("minh", null, "m1", null, true); // Minh gui lenh xoa ma tin cua An
        receiveMessage("an", null, "m1", "loi An noi");

        assertEquals("loi An noi", shown.get(0).getText());
        assertEquals(1, history().snapshot().size());
    }

    @Test
    void normalOrderStillWorks() throws Exception {
        receiveMessage("an", null, "m1", "7 gio");
        receiveEdit("an", null, "m1", "8 gio", false);

        assertEquals("7 gio", shown.get(0).getText());
        assertEquals("an/m1=8 gio", edits.get(0));
        assertEquals("8 gio", history().snapshot().get(0).text());
        receiveMessage("an", null, "m2", "tin khac");
        assertEquals("tin khac", shown.get(1).getText(), "Lenh da ap dung khong treo lai cho tin sau");
    }

    private void receiveMessage(String peerId, String namespace, String id, String text) throws Exception {
        invoke("handleMessage", peerId, new Envelope(EnvelopeType.MESSAGE, namespace, System.currentTimeMillis(),
                JSON.writeValueAsBytes(new MessagePayload(id, "ai-cung-duoc", text, System.currentTimeMillis(), null))));
    }

    private void receiveEdit(String peerId, String namespace, String id, String text, boolean deleted) throws Exception {
        invoke("handleMessageEdit", peerId, new Envelope(EnvelopeType.MESSAGE_EDIT, namespace, System.currentTimeMillis(),
                JSON.writeValueAsBytes(new MessageEditPayload(UUID.randomUUID().toString(), id, text, deleted))));
    }

    private void invoke(String method, String peerId, Envelope envelope) throws Exception {
        Method handler = P2pChatSession.class.getDeclaredMethod(method, String.class, Envelope.class);
        handler.setAccessible(true);
        handler.invoke(session, peerId, envelope);
    }

    private GroupHistory history() throws Exception {
        Field field = P2pChatSession.class.getDeclaredField("history");
        field.setAccessible(true);
        return (GroupHistory) field.get(session);
    }
}
