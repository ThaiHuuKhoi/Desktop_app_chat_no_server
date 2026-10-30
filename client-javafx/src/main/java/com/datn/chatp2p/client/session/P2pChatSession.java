package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.RoomSession;
import com.datn.chatp2p.p2p.signaling.WebSocketSignalingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * ChatSession backed by the real signaling and ICE/P2P stack.
 */
public final class P2pChatSession implements ChatSession {

    private static final List<TransportAddress> STUN_SERVERS = List.of(
            new TransportAddress("stun.l.google.com", 19302, Transport.UDP));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RoomConfig config;
    private final String selfPeerId = UUID.randomUUID().toString();
    private final RoomSession roomSession;

    private volatile ChatSessionListener listener;
    private volatile boolean left;

    public P2pChatSession(RoomConfig config) {
        this.config = config;
        this.roomSession = new RoomSession(
                config.roomName(),
                selfPeerId,
                config.userName(),
                new WebSocketSignalingClient(),
                STUN_SERVERS);
    }

    @Override
    public String selfPeerId() {
        return selfPeerId;
    }

    @Override
    public void start(ChatSessionListener listener) {
        this.listener = listener;
        roomSession.onPeerJoined(this::handlePeerJoined);
        roomSession.onPeerLeft(peerId -> notifyPeerLeft(peerId));
        roomSession.onConnectionFailed((peerId, error) ->
                notifyConnectionFailed(peerId, error));
        roomSession.onEnvelope(EnvelopeType.MESSAGE, this::handleMessage);

        try {
            roomSession.join(config.serverUri());
        } catch (RuntimeException error) {
            notifyConnectionFailed("", error);
        }
    }

    @Override
    public void sendGroupMessage(ChatMessage message) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the gui tin nhan");
        }

        MessagePayload payload = new MessagePayload(
                UUID.randomUUID().toString(),
                message.getAuthorId(),
                message.getText(),
                System.currentTimeMillis());
        roomSession.broadcast(EnvelopeType.MESSAGE, payload);
    }

    @Override
    public void leave() {
        if (left) {
            return;
        }
        left = true;
        roomSession.leave();
    }

    private void handlePeerJoined(PeerConnection connection) {
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }

        currentListener.onPeerJoined(new SessionPeer(
                connection.getPeerId(),
                connection.getCustomUsername() == null
                        ? "Người dùng " + connection.getPeerId()
                        : connection.getCustomUsername(),
                null,
                null,
                SessionPeer.ConnectionKind.DIRECT));
    }

    private void handleMessage(String peerId, Envelope envelope) {
        MessagePayload payload = roomSessionPayload(envelope);
        ChatMessage message = new ChatMessage(peerId, payload.text());
        message.markReceived();

        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onMessageReceived(message);
        }
    }

    private MessagePayload roomSessionPayload(Envelope envelope) {
        // EnvelopeCodec is intentionally owned by each PeerConnection. The
        // RoomSession API currently exposes only the decoded envelope, so the
        // payload is parsed with the same Jackson-compatible record shape here.
        try {
            return OBJECT_MAPPER.readValue(envelope.payload(), MessagePayload.class);
        } catch (IOException error) {
            throw new IllegalStateException("Payload tin nhan khong hop le", error);
        }
    }

    private void notifyPeerLeft(String peerId) {
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onPeerLeft(peerId);
        }
    }

    private void notifyConnectionFailed(String peerId, Throwable error) {
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            currentListener.onPeerConnectionFailed(peerId, reason);
        }
    }
}
