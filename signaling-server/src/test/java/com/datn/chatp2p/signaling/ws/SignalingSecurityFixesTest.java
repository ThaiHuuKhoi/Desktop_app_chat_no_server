package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.JoinRejectReason;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hoi quy cho cac loi bao mat phia signaling server trong bao cao audit
 * (docs/Bao-cao-bao-mat-13-van-de.md): token bi relay, rang buoc session sau LEAVE, do dai truong JOIN.
 */
class SignalingSecurityFixesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Van de 2: peer dich KHONG duoc nhan authToken cua nguoi gui. */
    @Test
    void relayedSignalDoesNotCarryTheSendersAuthToken() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        FakeWebSocketSession bob = new FakeWebSocketSession("b");
        handler.handleTextMessage(alice, join("phong", "pa"));
        handler.handleTextMessage(bob, join("phong", "pb"));
        bob.getSentMessages().clear();

        SignalMessage offer = targeted(SignalType.OFFER, "phong", "pa", "pb");
        offer.setAuthToken("v1_secret-token-of-alice");
        handler.handleTextMessage(alice, text(offer));

        List<SignalMessage> received = decode(bob.getSentMessages());
        assertEquals(1, received.size(), "Bob phai nhan dung 1 OFFER");
        assertEquals(SignalType.OFFER, received.get(0).getType());
        assertNull(received.get(0).getAuthToken(), "Token cua Alice bi lo sang Bob");
    }

    /** Van de 4: sau LEAVE, session khong duoc relay vao phong cu duoi peerId cu. */
    @Test
    void sessionCannotRelayAfterLeavingTheRoom() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        FakeWebSocketSession bob = new FakeWebSocketSession("b");
        handler.handleTextMessage(alice, join("phong", "pa"));
        handler.handleTextMessage(bob, join("phong", "pb"));

        SignalMessage leave = new SignalMessage();
        leave.setType(SignalType.LEAVE);
        handler.handleTextMessage(alice, text(leave));
        bob.getSentMessages().clear();

        handler.handleTextMessage(alice, text(targeted(SignalType.ICE_CANDIDATE, "phong", "pa", "pb")));

        assertTrue(bob.getSentMessages().isEmpty(), "Session da LEAVE van relay duoc ICE toi Bob");
    }

    /** Van de 4 (bien the): JOIN lai that bai cung xoa rang buoc cu. */
    @Test
    void failedRejoinAlsoDropsTheOldBinding() throws Exception {
        RoomRegistry registry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(registry, objectMapper);
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        FakeWebSocketSession bob = new FakeWebSocketSession("b");
        handler.handleTextMessage(alice, join("phong", "pa"));
        handler.handleTextMessage(bob, join("phong", "pb"));
        // Lap day phong "day" de lan JOIN lai cua Alice bi tu choi ROOM_FULL.
        for (int i = 0; i < RoomRegistry.MAX_PEERS_PER_ROOM; i++) {
            handler.handleTextMessage(new FakeWebSocketSession("f" + i), join("day", "f" + i));
        }
        handler.handleTextMessage(alice, join("day", "pa2"));
        bob.getSentMessages().clear();

        handler.handleTextMessage(alice, text(targeted(SignalType.OFFER, "phong", "pa", "pb")));

        assertTrue(bob.getSentMessages().isEmpty(), "Session JOIN lai that bai van relay duoc vao phong cu");
    }

    /** Van de 12: roomId / peerId qua dai bi tu choi INVALID_JOIN, khong tao phong. */
    @Test
    void oversizedJoinFieldsAreRejected() throws Exception {
        RoomRegistry registry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(registry, objectMapper);
        FakeWebSocketSession client = new FakeWebSocketSession("c");

        handler.handleTextMessage(client, join("x".repeat(SignalingWebSocketHandler.MAX_ROOM_ID_LENGTH + 1), "p"));
        handler.handleTextMessage(client, join("phong", "p".repeat(SignalingWebSocketHandler.MAX_PEER_ID_LENGTH + 1)));

        List<SignalMessage> replies = decode(client.getSentMessages());
        assertEquals(2, replies.size());
        for (SignalMessage reply : replies) {
            assertEquals(SignalType.JOIN_REJECTED, reply.getType());
            assertEquals(JoinRejectReason.INVALID_JOIN, reply.getPayload());
        }
        assertTrue(registry.activeRooms().isEmpty(), "Khong duoc tao phong voi truong qua dai");
    }

    /** Van de 12: leaveBySession dung chi muc, van don dung peer khi dong ket noi. */
    @Test
    void closingTheConnectionStillRemovesThePeer() throws Exception {
        RoomRegistry registry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(registry, objectMapper);
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        handler.handleTextMessage(alice, join("phong", "pa"));
        assertFalse(registry.peersInRoom("phong").isEmpty());

        handler.afterConnectionClosed(alice, org.springframework.web.socket.CloseStatus.NORMAL);

        assertTrue(registry.peersInRoom("phong").isEmpty());
        assertTrue(registry.activeRooms().isEmpty());
    }

    // ------------------------------------------------------------ helpers

    private TextMessage join(String roomId, String peerId) throws Exception {
        SignalMessage join = new SignalMessage();
        join.setType(SignalType.JOIN);
        join.setRoomId(roomId);
        join.setFromPeerId(peerId);
        join.setUserName("user-" + peerId.substring(0, Math.min(8, peerId.length())));
        return text(join);
    }

    private static SignalMessage targeted(SignalType type, String roomId, String from, String to) {
        SignalMessage message = new SignalMessage();
        message.setType(type);
        message.setRoomId(roomId);
        message.setFromPeerId(from);
        message.setToPeerId(to);
        message.setPayload("payload");
        return message;
    }

    private TextMessage text(SignalMessage message) throws Exception {
        return new TextMessage(objectMapper.writeValueAsString(message));
    }

    private List<SignalMessage> decode(List<WebSocketMessage<?>> messages) throws Exception {
        java.util.ArrayList<SignalMessage> out = new java.util.ArrayList<>();
        for (WebSocketMessage<?> message : messages) {
            out.add(objectMapper.readValue(((TextMessage) message).getPayload(), SignalMessage.class));
        }
        return out;
    }
}
