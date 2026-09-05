package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 *
 * <p><b>Nguyen tac xu ly loi</b> (Tai-lieu-ky-thuat.md Phan H.1): server la
 * diem chung cua nhieu peer, nen 1 loi cuc bo (1 client gui JSON hong, 1
 * session dang dong dot ngot) KHONG duoc lam gian doan cac peer khac trong
 * cung phong. Vi vay:
 * <ul>
 *   <li>{@link #handleTextMessage} bat rieng loi parse JSON - log WARN, bo
 *       qua dung ban tin do, KHONG dong session cua nguoi gui.</li>
 *   <li>{@link #send} bat rieng loi gui toi TUNG session - 1 peer gui that
 *       bai (socket dang dong/loi mang) khong duoc lam dung vong lap
 *       {@link #broadcastToOthers}, cac peer con lai van phai nhan duoc
 *       thong bao binh thuong.</li>
 * </ul>
 * Ca 2 diem tren deu KHONG con method nao trong lop nay khai bao
 * {@code throws IOException}/{@code throws Exception} nua - moi loi co the
 * xay ra da duoc bat va xu ly ngay tai cho.
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
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SignalMessage signal;
        try {
            signal = objectMapper.readValue(message.getPayload(), SignalMessage.class);
        } catch (JsonProcessingException e) {
            // Client gui JSON hong/sai dinh dang - bo qua DUNG ban tin nay, khong
            // dong session (co the la bug o client, khong phai tan cong - van
            // nen cho client co co hoi gui ban tin dung sau do).
            log.warn("Bo qua ban tin JSON khong hop le tu session {}: {}", session.getId(), e.getMessage());
            return;
        }

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

    private void handleJoin(WebSocketSession session, SignalMessage signal) {
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

    private void handleLeave(WebSocketSession session) {
        removeAndNotify(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeAndNotify(session);
    }

    private void removeAndNotify(WebSocketSession session) {
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

    private void relayToTargetPeer(SignalMessage signal) {
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

    /**
     * Gui {@code message} toi tat ca peer trong phong tru {@code exceptPeerId}.
     * Moi lan goi {@link #send} da tu bat loi rieng cho TUNG session - 1 peer
     * gui that bai KHONG duoc lam dung vong lap, cac peer con lai van phai
     * nhan duoc thong bao.
     */
    private void broadcastToOthers(String roomId, String exceptPeerId, SignalMessage message) {
        for (PeerSession peer : roomRegistry.peersInRoom(roomId)) {
            if (!peer.getPeerId().equals(exceptPeerId)) {
                send(peer.getWebSocketSession(), message);
            }
        }
    }

    /**
     * Gui 1 ban tin toi 1 session - tu bat va nuot loi IOException (session
     * dang dong dot ngot, mat mang giua chung...) thay vi de no lan ra ngoai:
     * day la loi CUC BO cua 1 ket noi, khong duoc anh huong toi viec xu ly
     * ban tin/thong bao cho cac peer/session khac.
     */
    private void send(WebSocketSession session, SignalMessage message) {
        if (!session.isOpen()) {
            return;
        }
        // QUAN TRONG: WebSocketSession/Tomcat KHONG an toan khi 2 thread cung goi
        // sendMessage() dong thoi tren CUNG 1 session - se nem
        // IllegalStateException ("state [TEXT_PARTIAL_WRITING]..."). Dieu nay xay
        // ra that: nhieu peer JOIN gan nhu dong thoi (nhieu thread Tomcat khac
        // nhau) co the cung luc broadcastToOthers() toi CUNG 1 peer da co san -
        // phat hien qua WebSocketSignalingClientCapacityTest (30 peer join dong
        // thoi). Khoa tren chinh doi tuong session (khong phai khoa toan cuc) de
        // chi serialize ghi TREN CUNG 1 session - cac session khac nhau van gui
        // song song binh thuong, khong mat hieu nang.
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (IOException | IllegalStateException e) {
                log.warn("Gui ban tin {} toi session {} that bai (co the vua dong hoac dang ghi dong thoi) - bo qua, khong anh huong peer khac: {}",
                        message.getType(), session.getId(), e.getMessage());
            }
        }
    }
}
