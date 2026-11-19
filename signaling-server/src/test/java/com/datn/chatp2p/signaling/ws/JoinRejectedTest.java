package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.JoinRejectReason;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.datn.chatp2p.signaling.room.WrongPasswordLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Server bao ly do khi tu choi JOIN (Tai-lieu-ky-thuat.md muc J.2). */
class JoinRejectedTest {

    private static final String ROOM_T1 = "phong-rieng~aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ROOM_T2 = "phong-rieng~bbbbbbbbbbbbbbbbbbbbbbbb";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------ RoomRegistry

    @Test
    void sameNameDifferentTagIsWrongPassword() {
        RoomRegistry registry = new RoomRegistry();
        assertTrue(registry.tryJoin(peer("s1", ROOM_T1, "p1")).accepted());

        RoomRegistry.JoinResult wrong = registry.tryJoin(peer("s2", ROOM_T2, "p2"));

        assertEquals(JoinRejectReason.WRONG_PASSWORD, wrong.rejectReason());
        assertEquals(1, registry.activeRooms().size(), "Khong duoc tao them phong 'phong-rieng' thu 2");
        assertTrue(registry.tryJoin(peer("s3", ROOM_T1, "p3")).accepted(), "Dung mat khau van vao duoc");
    }

    @Test
    void afterEveryoneLeavesTheNameCanBeUsedWithAnotherPassword() {
        RoomRegistry registry = new RoomRegistry();
        registry.tryJoin(peer("s1", ROOM_T1, "p1"));
        registry.leave(ROOM_T1, "p1");

        assertTrue(registry.tryJoin(peer("s2", ROOM_T2, "p2")).accepted());
    }

    @Test
    void publicRoomWithSameNameDoesNotBlockPasswordRoom() {
        RoomRegistry registry = new RoomRegistry();
        registry.tryJoin(peer("s1", "phong-rieng", "p1"));

        assertTrue(registry.tryJoin(peer("s2", ROOM_T1, "p2")).accepted());
    }

    @Test
    void roomPrefixMustMatchWholeName() {
        RoomRegistry registry = new RoomRegistry();
        registry.tryJoin(peer("s1", "phong~aaaaaaaaaaaaaaaaaaaaaaaa", "p1"));

        // "phong-rieng" khong duoc coi la cung ten voi "phong"
        assertTrue(registry.tryJoin(peer("s2", ROOM_T2, "p2")).accepted());
    }

    @Test
    void fullRoomAndDuplicatePeerHaveTheirOwnReasons() {
        RoomRegistry registry = new RoomRegistry();
        for (int i = 0; i < RoomRegistry.MAX_PEERS_PER_ROOM; i++) {
            assertTrue(registry.tryJoin(peer("s" + i, "phong", "p" + i)).accepted());
        }

        assertEquals(JoinRejectReason.ROOM_FULL, registry.tryJoin(peer("sx", "phong", "px")).rejectReason());
        assertEquals(JoinRejectReason.DUPLICATE_PEER, registry.tryJoin(peer("sy", "phong", "p0")).rejectReason());
    }

    // ------------------------------------------------------------ handler

    @Test
    void handlerSendsJoinRejectedWithReason() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession owner = new FakeWebSocketSession("s1");
        FakeWebSocketSession intruder = new FakeWebSocketSession("s2");
        handler.handleTextMessage(owner, join(ROOM_T1, "p1"));

        handler.handleTextMessage(intruder, join(ROOM_T2, "p2"));

        SignalMessage reply = lastMessage(intruder);
        assertEquals(SignalType.JOIN_REJECTED, reply.getType());
        assertEquals(JoinRejectReason.WRONG_PASSWORD, reply.getPayload());
        assertTrue(reply.getPeers().isEmpty(), "Khong duoc gui danh sach peer cho nguoi bi tu choi");
        assertEquals(1, owner.getSentMessages().size(), "Chu phong chi nhan PEER_LIST cua minh, khong nhan PEER_JOINED");
    }

    @Test
    void handlerRateLimitsRepeatedWrongPasswords() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        handler.handleTextMessage(new FakeWebSocketSession("owner"), join(ROOM_T1, "p1"));
        FakeWebSocketSession guesser = new FakeWebSocketSession("guesser");

        for (int i = 0; i < WrongPasswordLimiter.DEFAULT_MAX_FAILURES; i++) {
            handler.handleTextMessage(guesser, join(ROOM_T2, "g" + i));
            assertEquals(JoinRejectReason.WRONG_PASSWORD, lastMessage(guesser).getPayload());
        }
        // Ke ca dung mat khau cung bi chan trong luc dang bi gioi han
        handler.handleTextMessage(guesser, join(ROOM_T1, "g-right"));

        assertEquals(JoinRejectReason.RATE_LIMITED, lastMessage(guesser).getPayload());
    }

    @Test
    void handlerAllowsJoinWhenPasswordMatches() throws Exception {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        handler.handleTextMessage(new FakeWebSocketSession("s1"), join(ROOM_T1, "p1"));
        FakeWebSocketSession friend = new FakeWebSocketSession("s2");

        handler.handleTextMessage(friend, join(ROOM_T1, "p2"));

        assertEquals(SignalType.PEER_LIST, lastMessage(friend).getType());
    }

    // ------------------------------------------------------------ limiter

    @Test
    void limiterForgetsFailuresAfterTheWindow() {
        AtomicLong now = new AtomicLong(1_000_000);
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
        WrongPasswordLimiter limiter = new WrongPasswordLimiter(2, 60_000, clock);

        limiter.recordFailure("1.2.3.4");
        assertFalse(limiter.isBlocked("1.2.3.4"));
        limiter.recordFailure("1.2.3.4");
        assertTrue(limiter.isBlocked("1.2.3.4"));
        assertFalse(limiter.isBlocked("5.6.7.8"), "Moi IP tinh rieng");

        now.addAndGet(60_001);
        assertFalse(limiter.isBlocked("1.2.3.4"), "Het 1 phut thi duoc thu lai");
    }

    // ------------------------------------------------------------ helpers

    private static PeerSession peer(String sessionId, String roomId, String peerId) {
        return new PeerSession(new FakeWebSocketSession(sessionId), roomId, peerId, "User " + peerId);
    }

    private TextMessage join(String roomId, String peerId) throws Exception {
        return new TextMessage(objectMapper.writeValueAsString(SignalMessage.join(roomId, peerId, "User " + peerId)));
    }

    private SignalMessage lastMessage(FakeWebSocketSession session) throws Exception {
        List<WebSocketMessage<?>> sent = session.getSentMessages();
        assertFalse(sent.isEmpty(), "Server phai tra loi");
        return objectMapper.readValue(((TextMessage) sent.get(sent.size() - 1)).getPayload(), SignalMessage.class);
    }
}
