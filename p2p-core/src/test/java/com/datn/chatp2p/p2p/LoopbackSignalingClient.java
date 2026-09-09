package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.p2p.signaling.SignalMessageDispatcher;
import com.datn.chatp2p.p2p.signaling.SignalingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Fake {@link SignalingClient} CHI DUNG CHO TEST - mo phong dung hanh vi cua
 * {@code signaling-server} that (JOIN -> PEER_LIST + broadcast PEER_JOINED,
 * relay OFFER/ANSWER theo dung toPeerId, LEAVE -> broadcast PEER_LEFT) nhung
 * hoan toan trong bo nho, khong qua WebSocket/mang that.
 *
 * <p>Nhieu instance dung chung 1 {@link Hub} duoc coi la "cung ket noi toi 1
 * signaling server" - dung de test {@link RoomSession} giua nhieu peer ma
 * khong can chay Spring Boot that (xem {@code RoomSessionTest}).
 */
final class LoopbackSignalingClient implements SignalingClient {

    /** "Ban sao" toi gian cua RoomRegistry + relay logic trong SignalingWebSocketHandler that. */
    static final class Hub {
        private final Map<String, Map<String, LoopbackSignalingClient>> roomsByRoomId = new ConcurrentHashMap<>();

        synchronized void join(String roomId, String peerId, String userName, LoopbackSignalingClient client) {
            Map<String, LoopbackSignalingClient> room =
                    roomsByRoomId.computeIfAbsent(roomId, r -> new ConcurrentHashMap<>());

            List<SignalMessage.PeerInfo> existingPeers = new ArrayList<>();
            for (Map.Entry<String, LoopbackSignalingClient> entry : room.entrySet()) {
                existingPeers.add(new SignalMessage.PeerInfo(entry.getKey(), entry.getValue().userName));
            }

            room.put(peerId, client);

            SignalMessage peerList = new SignalMessage();
            peerList.setType(SignalType.PEER_LIST);
            peerList.setRoomId(roomId);
            peerList.setPeers(existingPeers);
            client.deliver(peerList);

            SignalMessage peerJoined = new SignalMessage();
            peerJoined.setType(SignalType.PEER_JOINED);
            peerJoined.setRoomId(roomId);
            peerJoined.setFromPeerId(peerId);
            peerJoined.setUserName(userName);
            for (Map.Entry<String, LoopbackSignalingClient> entry : room.entrySet()) {
                if (!entry.getKey().equals(peerId)) {
                    entry.getValue().deliver(peerJoined);
                }
            }
        }

        synchronized void leave(String roomId, String peerId) {
            Map<String, LoopbackSignalingClient> room = roomsByRoomId.get(roomId);
            if (room == null) {
                return;
            }
            room.remove(peerId);

            SignalMessage peerLeft = new SignalMessage();
            peerLeft.setType(SignalType.PEER_LEFT);
            peerLeft.setRoomId(roomId);
            peerLeft.setFromPeerId(peerId);
            for (LoopbackSignalingClient other : room.values()) {
                other.deliver(peerLeft);
            }
        }

        synchronized void relay(String roomId, SignalMessage message) {
            Map<String, LoopbackSignalingClient> room = roomsByRoomId.get(roomId);
            if (room == null) {
                return;
            }
            LoopbackSignalingClient target = room.get(message.getToPeerId());
            if (target != null) {
                target.deliver(message);
            }
        }
    }

    private final Hub hub;
    private final SignalMessageDispatcher dispatcher = new SignalMessageDispatcher();

    private volatile String roomId;
    private volatile String peerId;
    private volatile String userName;

    LoopbackSignalingClient(Hub hub) {
        this.hub = hub;
    }

    @Override
    public void connect(String serverUri, String roomId, String peerId, String userName) {
        this.roomId = roomId;
        this.peerId = peerId;
        this.userName = userName;
        hub.join(roomId, peerId, userName, this);
    }

    @Override
    public void onPeerJoined(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.PEER_JOINED, handler);
    }

    @Override
    public void onPeerLeft(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.PEER_LEFT, handler);
    }

    @Override
    public void onPeerList(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.PEER_LIST, handler);
    }

    @Override
    public void onOffer(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.OFFER, handler);
    }

    @Override
    public void onAnswer(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.ANSWER, handler);
    }

    @Override
    public void onIceCandidate(Consumer<SignalMessage> handler) {
        dispatcher.register(SignalType.ICE_CANDIDATE, handler);
    }

    @Override
    public void sendOffer(String toPeerId, String sdp) {
        sendTargeted(SignalType.OFFER, toPeerId, sdp);
    }

    @Override
    public void sendAnswer(String toPeerId, String sdp) {
        sendTargeted(SignalType.ANSWER, toPeerId, sdp);
    }

    @Override
    public void sendIceCandidate(String toPeerId, String candidate) {
        sendTargeted(SignalType.ICE_CANDIDATE, toPeerId, candidate);
    }

    @Override
    public void disconnect() {
        hub.leave(roomId, peerId);
    }

    private void sendTargeted(SignalType type, String toPeerId, String payload) {
        SignalMessage message = new SignalMessage();
        message.setType(type);
        message.setRoomId(roomId);
        message.setFromPeerId(peerId);
        message.setToPeerId(toPeerId);
        message.setPayload(payload);
        hub.relay(roomId, message);
    }

    private void deliver(SignalMessage message) {
        dispatcher.dispatch(message);
    }
}
