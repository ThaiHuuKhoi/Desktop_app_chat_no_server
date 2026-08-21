package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handler WebSocket cho giao thuc signaling toi gian (De-cuong-Chat-P2P-Java.md
 * muc 6). Chi lam 2 viec: (1) quan ly ai dang o phong nao qua {@link RoomRegistry},
 * (2) relay nguyen ven ban tin OFFER/ANSWER/ICE_CANDIDATE toi dung peer dich -
 * khong bao gio parse hay dien giai noi dung ben trong payload.
 */
@Component
public class SignalingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingWebSocketHandler.class);

    private static final String ROOM_ID_ATTR = "roomId";
    private static final String PEER_ID_ATTR = "peerId";

    private final RoomRegistry roomRegistry;
    private final ObjectMapper objectMapper;

    public SignalingWebSocketHandler(RoomRegistry roomRegistry, ObjectMapper objectMapper) {
        this.roomRegistry = roomRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalMessage signal = objectMapper.readValue(message.getPayload(), SignalMessage.class);
        if (signal.getType() == null) {
            log.warn("Bo qua ban tin thieu type tu session {}", session.getId());
            return;
        }

        switch (signal.getType()) {
            case JOIN -> handleJoin(session, signal);
            case LEAVE -> handleLeave(session);
            case OFFER, ANSWER, ICE_CANDIDATE -> relayToTargetPeer(signal);
            case PEER_JOINED, PEER_LEFT, PEER_LIST ->
                    log.debug("Bo qua ban tin chi server moi duoc phat: {}", signal.getType());
        }
    }

    private void handleJoin(WebSocketSession session, SignalMessage signal) throws IOException {
        PeerSession self = new PeerSession(session, signal.getRoomId(), signal.getFromPeerId(), signal.getUserName());
        session.getAttributes().put(ROOM_ID_ATTR, self.getRoomId());
        session.getAttributes().put(PEER_ID_ATTR, self.getPeerId());

        List<PeerSession> existingPeers = roomRegistry.join(self);

        send(session, peerListMessage(self.getRoomId(), existingPeers));

        SignalMessage peerJoined = new SignalMessage();
        peerJoined.setType(SignalType.PEER_JOINED);
        peerJoined.setRoomId(self.getRoomId());
        peerJoined.setFromPeerId(self.getPeerId());
        peerJoined.setUserName(self.getUserName());
        broadcastToOthers(self.getRoomId(), self.getPeerId(), peerJoined);

        log.info("Peer {} ({}) da vao phong {}", self.getPeerId(), self.getUserName(), self.getRoomId());
    }

    private void handleLeave(WebSocketSession session) throws IOException {
        removeAndNotify(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeAndNotify(session);
    }

    private void removeAndNotify(WebSocketSession session) throws IOException {
        Optional<PeerSession> removed = roomRegistry.leaveBySession(session);
        if (removed.isEmpty()) {
            return;
        }
        PeerSession peer = removed.get();
        SignalMessage peerLeft = new SignalMessage();
        peerLeft.setType(SignalType.PEER_LEFT);
        peerLeft.setRoomId(peer.getRoomId());
        peerLeft.setFromPeerId(peer.getPeerId());
        broadcastToOthers(peer.getRoomId(), peer.getPeerId(), peerLeft);

        log.info("Peer {} ({}) da roi phong {}", peer.getPeerId(), peer.getUserName(), peer.getRoomId());
    }

    private void relayToTargetPeer(SignalMessage signal) throws IOException {
        if (signal.getRoomId() == null || signal.getToPeerId() == null) {
            log.warn("Ban tin {} thieu roomId/toPeerId, bo qua", signal.getType());
            return;
        }
        Optional<PeerSession> target = roomRegistry.find(signal.getRoomId(), signal.getToPeerId());
        if (target.isEmpty()) {
            log.warn("Khong tim thay peer dich {} trong phong {} de relay {}",
                    signal.getToPeerId(), signal.getRoomId(), signal.getType());
            return;
        }
        send(target.get().getWebSocketSession(), signal);
    }

    private SignalMessage peerListMessage(String roomId, List<PeerSession> peers) {
        SignalMessage message = new SignalMessage();
        message.setType(SignalType.PEER_LIST);
        message.setRoomId(roomId);
        message.setPeers(peers.stream()
                .map(p -> new SignalMessage.PeerInfo(p.getPeerId(), p.getUserName()))
                .collect(Collectors.toList()));
        return message;
    }

    private void broadcastToOthers(String roomId, String exceptPeerId, SignalMessage message) throws IOException {
        for (PeerSession peer : roomRegistry.peersInRoom(roomId)) {
            if (!peer.getPeerId().equals(exceptPeerId)) {
                send(peer.getWebSocketSession(), message);
            }
        }
    }

    private void send(WebSocketSession session, SignalMessage message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
    }
}
