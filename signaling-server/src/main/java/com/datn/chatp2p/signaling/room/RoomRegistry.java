package com.datn.chatp2p.signaling.room;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * So sach cac phong va cac peer dang co mat trong tung phong, giu hoan toan
 * trong bo nho (khong ben vung - phu hop voi nguyen tac ephemeral cua de
 * cuong: khong luu du lieu tren server trung gian).
 *
 * <p>Chi luu metadata ket noi toi gian (roomId, peerId, userName) - khong bao
 * gio cham vao noi dung tin nhan/SDP, chi lam nhiem vu relay.
 */
@Component
public class RoomRegistry {

    private final Map<String, Map<String, PeerSession>> roomsByRoomId = new ConcurrentHashMap<>();

    /**
     * Them mot peer vao phong, tao phong neu chua ton tai. Tra ve danh sach cac
     * peer da co san.
     *
     * <p><b>Phai synchronized tren {@code room}:</b> neu khong, 2 peer JOIN gan
     * nhu cung luc (2 thread Tomcat khac nhau xu ly 2 WebSocket session khac
     * nhau) co the CUNG doc duoc {@code existingPeers} rong truoc khi ca 2 kip
     * them minh vao map - ket qua ca 2 ben deu nghi minh la nguoi dau tien,
     * khong ai chu dong gui OFFER, ket noi P2P treo vinh vien. Loi nay co that,
     * phat hien qua {@code RoomSessionRealSignalingServerTest} (module p2p-core)
     * khi 2 RoomSession that join gan nhu dong thoi qua 1 signaling-server that.
     */
    public List<PeerSession> join(PeerSession newPeer) {
        Map<String, PeerSession> room =
                roomsByRoomId.computeIfAbsent(newPeer.getRoomId(), roomId -> new ConcurrentHashMap<>());
        synchronized (room) {
            List<PeerSession> existingPeers = List.copyOf(room.values());
            room.put(newPeer.getPeerId(), newPeer);
            return existingPeers;
        }
    }

    /** Xoa mot peer khoi phong (khi roi phong hoac mat ket noi). Tra ve peer vua bi xoa, neu co. */
    public Optional<PeerSession> leave(String roomId, String peerId) {
        Map<String, PeerSession> room = roomsByRoomId.get(roomId);
        if (room == null) {
            return Optional.empty();
        }
        // Dong bo tren cung 1 doi tuong "room" nhu join() - tranh 1 peer dang
        // roi phong giua luc peer khac dang doc existingPeers de join.
        synchronized (room) {
            PeerSession removed = room.remove(peerId);
            if (room.isEmpty()) {
                roomsByRoomId.remove(roomId, room);
            }
            return Optional.ofNullable(removed);
        }
    }

    public Collection<PeerSession> peersInRoom(String roomId) {
        Map<String, PeerSession> room = roomsByRoomId.get(roomId);
        return room == null ? List.of() : List.copyOf(room.values());
    }

    public Optional<PeerSession> find(String roomId, String peerId) {
        Map<String, PeerSession> room = roomsByRoomId.get(roomId);
        return room == null ? Optional.empty() : Optional.ofNullable(room.get(peerId));
    }

    /** Tim va xoa peer gan voi mot WebSocketSession bi dong (duyet tat ca phong). */
    public Optional<PeerSession> leaveBySession(WebSocketSession session) {
        for (Map<String, PeerSession> room : roomsByRoomId.values()) {
            for (PeerSession peer : room.values()) {
                if (peer.getWebSocketSession().getId().equals(session.getId())) {
                    return leave(peer.getRoomId(), peer.getPeerId());
                }
            }
        }
        return Optional.empty();
    }
}
