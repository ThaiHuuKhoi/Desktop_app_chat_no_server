package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.JoinRejectReason;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server bat buoc dang nhap, ve cua 1 nguoi het han trong luc dang o phong: truoc day server chi lang le
 * bo ban tin OFFER/ICE cua ho (khong ai biet vi sao khong noi duoc). Nay server bao AUTH_EXPIRED cho nguoi
 * do va bao PEER_LEFT cho nhung nguoi con lai.
 */
class SessionExpiryRelayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

    private final AuthService auth = new AuthService(null) {
        @Override
        public Optional<AuthUser> authenticate(String token) {
            return validTokens.contains(token) ? Optional.of(new AuthUser(token.hashCode(), token.substring(3), null))
                    : Optional.empty();
        }
    };

    @Test
    void expiredSessionIsToldAndRemovedInsteadOfSilentlyIgnored() throws Exception {
        validTokens.add("v1_an");
        validTokens.add("v1_binh");
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper, auth,
                true, SignalingWebSocketHandler.MAX_CONNECTIONS_PER_ADDRESS);
        FakeWebSocketSession an = new FakeWebSocketSession("a");
        FakeWebSocketSession binh = new FakeWebSocketSession("b");
        handler.handleTextMessage(an, join("phong", "pa", "v1_an"));
        handler.handleTextMessage(binh, join("phong", "pb", "v1_binh"));
        an.getSentMessages().clear();
        binh.getSentMessages().clear();

        validTokens.remove("v1_an"); // 12 gio troi qua, hoac An doi mat khau tren may khac
        handler.handleTextMessage(an, offer("pa", "pb"));

        List<SignalMessage> toAn = decode(an.getSentMessages());
        assertEquals(1, toAn.size());
        assertEquals(SignalType.JOIN_REJECTED, toAn.get(0).getType());
        assertEquals(JoinRejectReason.AUTH_EXPIRED, toAn.get(0).getPayload());

        List<SignalMessage> toBinh = decode(binh.getSentMessages());
        assertTrue(toBinh.stream().noneMatch(m -> m.getType() == SignalType.OFFER), "Ban tin van khong duoc chuyen");
        assertTrue(toBinh.stream().anyMatch(m -> m.getType() == SignalType.PEER_LEFT && "pa".equals(m.getFromPeerId())),
                "Nguoi con lai biet An da roi phong");
    }

    @Test
    void validSessionStillRelays() throws Exception {
        validTokens.add("v1_an");
        validTokens.add("v1_binh");
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper, auth,
                true, SignalingWebSocketHandler.MAX_CONNECTIONS_PER_ADDRESS);
        FakeWebSocketSession an = new FakeWebSocketSession("a");
        FakeWebSocketSession binh = new FakeWebSocketSession("b");
        handler.handleTextMessage(an, join("phong", "pa", "v1_an"));
        handler.handleTextMessage(binh, join("phong", "pb", "v1_binh"));
        binh.getSentMessages().clear();

        handler.handleTextMessage(an, offer("pa", "pb"));

        assertTrue(decode(binh.getSentMessages()).stream().anyMatch(m -> m.getType() == SignalType.OFFER));
    }

    private TextMessage join(String roomId, String peerId, String token) throws Exception {
        SignalMessage join = SignalMessage.join(roomId, peerId, "ten");
        join.setAuthToken(token);
        return new TextMessage(objectMapper.writeValueAsString(join));
    }

    private TextMessage offer(String from, String to) throws Exception {
        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("phong");
        offer.setFromPeerId(from);
        offer.setToPeerId(to);
        offer.setPayload("sdp");
        return new TextMessage(objectMapper.writeValueAsString(offer));
    }

    private List<SignalMessage> decode(List<WebSocketMessage<?>> messages) throws Exception {
        List<SignalMessage> out = new ArrayList<>();
        for (WebSocketMessage<?> message : messages) {
            out.add(objectMapper.readValue(((TextMessage) message).getPayload(), SignalMessage.class));
        }
        return out;
    }
}
