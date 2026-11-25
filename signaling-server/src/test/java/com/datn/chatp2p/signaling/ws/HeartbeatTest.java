package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phat hien ket noi chet bang Ping/Pong. Loi do duoc khi test 2 may 25/11: tat Wi-Fi dot ngot khong dong TCP,
 * server ~4-5 phut sau moi bao PEER_LEFT.
 */
class HeartbeatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void silentSessionIsRemovedAndRoomIsToldWhileResponsiveSessionGetsPinged() throws Exception {
        RoomRegistry registry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(registry, objectMapper);
        FakeWebSocketSession alice = new FakeWebSocketSession("alice-session");
        FakeWebSocketSession bob = new FakeWebSocketSession("bob-session");
        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(bob);
        handler.handleTextMessage(alice, join("phong", "alice"));
        handler.handleTextMessage(bob, join("phong", "bob"));

        long now = System.currentTimeMillis() + 10 * SignalingWebSocketHandler.HEARTBEAT_TIMEOUT_MILLIS;
        alice.getAttributes().put(SignalingWebSocketHandler.LAST_SEEN_ATTR, now - 1_000); // vua tra Pong
        bob.getAttributes().put(SignalingWebSocketHandler.LAST_SEEN_ATTR,
                now - SignalingWebSocketHandler.HEARTBEAT_TIMEOUT_MILLIS - 1); // im lang qua han

        handler.checkHeartbeats(now);

        assertFalse(bob.isOpen(), "Ket noi im lang qua han phai bi dong");
        assertEquals(1, registry.peersInRoom("phong").size(), "Bob phai bi xoa khoi phong ngay");
        assertTrue(alice.isOpen());
        assertTrue(alice.getSentMessages().stream().anyMatch(message -> message instanceof PingMessage),
                "Ket noi con song phai duoc gui Ping");
        assertTrue(alice.getSentMessages().stream().anyMatch(this::isPeerLeftOfBob),
                "Alice phai nhan PEER_LEFT cua Bob");
    }

    @Test
    void pongAndAnyTextMessageCountAsAlive() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession session = new FakeWebSocketSession("s");
        handler.afterConnectionEstablished(session);
        session.getAttributes().put(SignalingWebSocketHandler.LAST_SEEN_ATTR, 0L);

        handler.handlePongMessage(session, new PongMessage());

        long seen = (Long) session.getAttributes().get(SignalingWebSocketHandler.LAST_SEEN_ATTR);
        assertTrue(seen > 0, "Pong phai cap nhat thoi diem con song");

        session.getAttributes().put(SignalingWebSocketHandler.LAST_SEEN_ATTR, 0L);
        handler.handleTextMessage(session, join("phong", "p1"));
        assertTrue((Long) session.getAttributes().get(SignalingWebSocketHandler.LAST_SEEN_ATTR) > 0);
    }

    private boolean isPeerLeftOfBob(WebSocketMessage<?> message) {
        if (!(message instanceof TextMessage text)) {
            return false;
        }
        try {
            SignalMessage signal = objectMapper.readValue(text.getPayload(), SignalMessage.class);
            return signal.getType() == SignalType.PEER_LEFT && "bob".equals(signal.getFromPeerId());
        } catch (Exception e) {
            return false;
        }
    }

    private TextMessage join(String roomId, String peerId) throws Exception {
        return new TextMessage(objectMapper.writeValueAsString(SignalMessage.join(roomId, peerId, "User " + peerId)));
    }
}
