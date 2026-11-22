package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.auth.AuthService;
import com.datn.chatp2p.signaling.auth.AuthUser;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server bao cho nguoi trong phong ai la TAI KHOAN (kem khoa danh tinh da dang ky) va ai la KHACH tu dat
 * ten (Co-che-xac-thuc-danh-tinh.md Buoc 7) - va khong de nguoi gui tu dien cac truong nay vao ban tin relay.
 */
class PeerAccountInfoTest {

    private static final String ALICE_TOKEN = "v1_token-alice";
    private static final String ALICE_KEY = "QUxJQ0UtSURFTlRJVFktS0VZ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** AuthService gia: chi token cua Alice hop le, khong can CSDL. */
    private final AuthService auth = new AuthService(null) {
        @Override
        public Optional<AuthUser> authenticate(String token) {
            return ALICE_TOKEN.equals(token) ? Optional.of(new AuthUser(1, "alice", ALICE_KEY)) : Optional.empty();
        }
    };

    private SignalingWebSocketHandler handler() {
        return new SignalingWebSocketHandler(new RoomRegistry(), objectMapper, auth, false,
                SignalingWebSocketHandler.MAX_CONNECTIONS_PER_ADDRESS);
    }

    @Test
    void peerJoinedTellsOthersWhetherTheNewcomerIsAnAccount() throws Exception {
        SignalingWebSocketHandler handler = handler();
        FakeWebSocketSession guest = new FakeWebSocketSession("g");
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        handler.handleTextMessage(guest, join("phong", "pg", "alice", null));
        guest.getSentMessages().clear();

        handler.handleTextMessage(alice, join("phong", "pa", "ten-gi-cung-duoc", ALICE_TOKEN));

        SignalMessage joined = only(decode(guest.getSentMessages()), SignalType.PEER_JOINED);
        assertEquals("alice", joined.getUserName(), "Server phai ep ten = ten tai khoan");
        assertEquals(Boolean.TRUE, joined.getAuthenticated());
        assertEquals(ALICE_KEY, joined.getIdentityKey());
    }

    @Test
    void peerListMarksGuestsAndAccounts() throws Exception {
        SignalingWebSocketHandler handler = handler();
        handler.handleTextMessage(new FakeWebSocketSession("a"), join("phong", "pa", "x", ALICE_TOKEN));
        handler.handleTextMessage(new FakeWebSocketSession("g"), join("phong", "pg", "alice", null));
        FakeWebSocketSession late = new FakeWebSocketSession("l");

        handler.handleTextMessage(late, join("phong", "pl", "late", null));

        SignalMessage list = only(decode(late.getSentMessages()), SignalType.PEER_LIST);
        assertEquals(2, list.getPeers().size());
        for (SignalMessage.PeerInfo info : list.getPeers()) {
            if (info.getPeerId().equals("pa")) {
                assertEquals(Boolean.TRUE, info.getAuthenticated());
                assertEquals(ALICE_KEY, info.getIdentityKey());
            } else {
                assertEquals("alice", info.getUserName(), "Khach van dat duoc ten trung tai khoan...");
                assertEquals(Boolean.FALSE, info.getAuthenticated(), "...nhung phai bi danh dau la khach");
                assertNull(info.getIdentityKey());
            }
        }
    }

    @Test
    void relayedOfferCannotClaimToBeAnAccount() throws Exception {
        SignalingWebSocketHandler handler = handler();
        FakeWebSocketSession guest = new FakeWebSocketSession("g");
        FakeWebSocketSession bob = new FakeWebSocketSession("b");
        handler.handleTextMessage(guest, join("phong", "pg", "alice", null));
        handler.handleTextMessage(bob, join("phong", "pb", "bob", null));
        bob.getSentMessages().clear();

        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("phong");
        offer.setFromPeerId("pg");
        offer.setToPeerId("pb");
        offer.setPayload("payload");
        offer.setAuthenticated(Boolean.TRUE);
        offer.setIdentityKey("KHOA-GIA");
        offer.getPeers().add(new SignalMessage.PeerInfo("px", "alice", Boolean.TRUE, "KHOA-GIA"));
        handler.handleTextMessage(guest, new TextMessage(objectMapper.writeValueAsString(offer)));

        SignalMessage relayed = only(decode(bob.getSentMessages()), SignalType.OFFER);
        assertNull(relayed.getAuthenticated(), "Khach tu nhan la tai khoan qua ban tin relay");
        assertNull(relayed.getIdentityKey());
        assertTrue(relayed.getPeers().isEmpty());
    }

    /**
     * Client cu (da phat hanh) doc JSON bang ObjectMapper mac dinh - gap truong la la loi parse ca ban tin.
     * Server moi phai gui cho no ban tin y nhu truoc: khong co authenticated / identityKey / protocolVersion,
     * ke ca dang null, trong moi loai ban tin no nhan (PEER_LIST, PEER_JOINED, OFFER relay).
     */
    @Test
    void legacyClientsReceiveOnlyFieldsTheyKnow() throws Exception {
        SignalingWebSocketHandler handler = handler();
        FakeWebSocketSession alice = new FakeWebSocketSession("a");
        FakeWebSocketSession legacy = new FakeWebSocketSession("o");
        handler.handleTextMessage(alice, join("phong", "pa", "x", ALICE_TOKEN));
        handler.handleTextMessage(legacy, legacyJoin("phong", "po", "old"));
        handler.handleTextMessage(new FakeWebSocketSession("n"), join("phong", "pn", "new", ALICE_TOKEN));
        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("phong");
        offer.setFromPeerId("pa");
        offer.setToPeerId("po");
        offer.setPayload("payload");
        offer.setProtocolVersion(SignalMessage.ACCOUNT_INFO_PROTOCOL_VERSION);
        handler.handleTextMessage(alice, new TextMessage(objectMapper.writeValueAsString(offer)));

        List<WebSocketMessage<?>> received = legacy.getSentMessages();
        assertEquals(3, received.size(), "Client cu phai nhan PEER_LIST, PEER_JOINED va OFFER");
        com.fasterxml.jackson.databind.ObjectMapper strict = new com.fasterxml.jackson.databind.ObjectMapper();
        for (WebSocketMessage<?> message : received) {
            String json = ((TextMessage) message).getPayload();
            for (String field : List.of("authenticated", "identityKey", "protocolVersion")) {
                assertTrue(!json.contains("\"" + field + "\""), "Client cu nhan truong la '" + field + "': " + json);
            }
            // Doc duoc bang ObjectMapper mac dinh (FAIL_ON_UNKNOWN_PROPERTIES = true) voi lop cu - o day chi
            // kiem tra cac truong cu con du.
            com.fasterxml.jackson.databind.JsonNode node = strict.readTree(json);
            assertTrue(node.has("type"));
        }
    }

    // ------------------------------------------------------------ helpers

    /** JOIN cua client MOI (gui protocolVersion qua {@link SignalMessage#join}). */
    private TextMessage join(String roomId, String peerId, String userName, String token) throws Exception {
        SignalMessage join = SignalMessage.join(roomId, peerId, userName);
        join.setAuthToken(token);
        return new TextMessage(objectMapper.writeValueAsString(join));
    }

    /** JOIN cua client CU: khong co protocolVersion, va client cu gui JSON khong co truong moi. */
    private static TextMessage legacyJoin(String roomId, String peerId, String userName) {
        return new TextMessage("{\"type\":\"JOIN\",\"roomId\":\"" + roomId + "\",\"fromPeerId\":\"" + peerId
                + "\",\"userName\":\"" + userName + "\",\"peers\":[]}");
    }

    private List<SignalMessage> decode(List<WebSocketMessage<?>> messages) throws Exception {
        List<SignalMessage> out = new ArrayList<>();
        for (WebSocketMessage<?> message : messages) {
            out.add(objectMapper.readValue(((TextMessage) message).getPayload(), SignalMessage.class));
        }
        return out;
    }

    private static SignalMessage only(List<SignalMessage> messages, SignalType type) {
        List<SignalMessage> matching = messages.stream().filter(m -> m.getType() == type).toList();
        assertEquals(1, matching.size(), "Can dung 1 ban tin " + type + ", nhan duoc " + messages);
        return matching.get(0);
    }
}
