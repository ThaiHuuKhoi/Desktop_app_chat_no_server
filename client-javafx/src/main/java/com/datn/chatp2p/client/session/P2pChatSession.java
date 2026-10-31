package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.IdentityPayload;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.RoomSession;
import com.datn.chatp2p.p2p.ice.TurnServerConfig;
import com.datn.chatp2p.p2p.signaling.WebSocketSignalingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

/**
 * ChatSession backed by the real signaling and ICE/P2P stack.
 */
public final class P2pChatSession implements ChatSession {

    private static final List<TransportAddress> STUN_SERVERS = List.of(
            new TransportAddress("stun.l.google.com", 19302, Transport.UDP));
    private static final List<TurnServerConfig> TURN_SERVERS = loadTurnServers();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RoomConfig config;
    private final WebSocketSignalingClient signalingClient;
    private final String selfPeerId = UUID.randomUUID().toString();
    private final PeerIdentityService identityService = new PeerIdentityService();
    private final Map<String, SessionPeer> knownPeers = new ConcurrentHashMap<>();
    private final Map<String, String> verifiedRemoteFingerprints = new ConcurrentHashMap<>();
    private final ScheduledExecutorService identityRetryExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "p2p-identity-retry");
                thread.setDaemon(true);
                return thread;
            });
    private final RoomSession roomSession;

    private volatile ChatSessionListener listener;
    private volatile boolean left;

    public P2pChatSession(RoomConfig config) {
        this.config = config;
        this.signalingClient = new WebSocketSignalingClient();
        this.signalingClient.setAuthToken(config.authToken());
        this.roomSession = new RoomSession(
                config.roomName(),
                selfPeerId,
                config.userName(),
                signalingClient,
                STUN_SERVERS,
                TURN_SERVERS);
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
        roomSession.onEnvelope(EnvelopeType.PEER_IDENTITY, this::handlePeerIdentity);

        try {
            roomSession.join(config.serverUri());
        } catch (RuntimeException error) {
            notifyConnectionFailed("", error);
        }
    }

    private void sendIdentityWithRetry(PeerConnection connection) {
        sendIdentity(connection);
        identityRetryExecutor.schedule(() -> {
            if (!left && roomSession.getPeerIds().contains(connection.getPeerId())) {
                sendIdentity(connection);
            }
        }, 400, TimeUnit.MILLISECONDS);
        identityRetryExecutor.schedule(() -> {
            if (!left && roomSession.getPeerIds().contains(connection.getPeerId())) {
                sendIdentity(connection);
            }
        }, 1200, TimeUnit.MILLISECONDS);
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
    public void sendDirectMessage(String peerId, ChatMessage message) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the gui tin nhan");
        }
        MessagePayload payload = new MessagePayload(
                UUID.randomUUID().toString(),
                message.getAuthorId(),
                message.getText(),
                System.currentTimeMillis());
        roomSession.sendTo(peerId, EnvelopeType.MESSAGE, EnvelopeNamespace.DIRECT_MESSAGE, payload);
    }

    @Override
    public void leave() {
        if (left) {
            return;
        }
        left = true;
        identityRetryExecutor.shutdownNow();
        roomSession.leave();
    }

    private void handlePeerJoined(PeerConnection connection) {
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }

        String remoteFingerprint = verifiedRemoteFingerprints.get(connection.getPeerId());
        SessionPeer peer = new SessionPeer(
                connection.getPeerId(),
                connection.getCustomUsername() == null
                        ? "Người dùng " + connection.getPeerId()
                        : connection.getCustomUsername(),
                identityService.localFingerprint(),
                remoteFingerprint,
                SessionPeer.ConnectionKind.DIRECT,
                remoteFingerprint == null
                        ? PeerVerificationState.VERIFYING
                        : PeerVerificationState.VERIFIED);
        knownPeers.put(connection.getPeerId(), peer);
        currentListener.onPeerJoined(peer);
        sendIdentityWithRetry(connection);
    }

    private void handleMessage(String peerId, Envelope envelope) {
        MessagePayload payload = roomSessionPayload(envelope);
        ChatMessage message = new ChatMessage(peerId, payload.text());
        message.markReceived();

        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            if (EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace())) {
                currentListener.onDirectMessageReceived(peerId, message);
            } else {
                currentListener.onMessageReceived(message);
            }
        }
    }

    private void sendIdentity(PeerConnection connection) {
        IdentityPayload payload = identityService.createPayload(config.roomName(), selfPeerId);
        try {
            connection.send(EnvelopeType.PEER_IDENTITY, payload);
        } catch (RuntimeException error) {
            notifyConnectionFailed(connection.getPeerId(), error);
        }
    }

    private void handlePeerIdentity(String peerId, Envelope envelope) {
        try {
            IdentityPayload payload = OBJECT_MAPPER.readValue(envelope.payload(), IdentityPayload.class);
            PeerIdentityService.Verification verification =
                    identityService.verify(payload, config.roomName(), peerId);
            if (!verification.valid()) {
                notifyConnectionFailed(peerId, new SecurityException("Identity peer khong hop le"));
                return;
            }

            String remoteFingerprint = verification.fingerprint();
            verifiedRemoteFingerprints.put(peerId, remoteFingerprint);
            SessionPeer current = knownPeers.get(peerId);
            if (current == null) {
                return;
            }
            SessionPeer updated = new SessionPeer(
                    current.peerId(),
                    current.displayName(),
                    current.localFingerprint(),
                    remoteFingerprint,
                    current.connectionKind(),
                    PeerVerificationState.VERIFIED);
            knownPeers.put(peerId, updated);
            ChatSessionListener currentListener = listener;
            if (currentListener != null && !left) {
                currentListener.onPeerIdentityUpdated(updated);
            }
        } catch (IllegalArgumentException | IOException error) {
            notifyConnectionFailed(peerId, new SecurityException("Identity peer khong hop le", error));
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
        knownPeers.remove(peerId);
        verifiedRemoteFingerprints.remove(peerId);
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

    private static List<TurnServerConfig> loadTurnServers() {
        String host = setting("CHAT_P2P_TURN_HOST");
        String portValue = setting("CHAT_P2P_TURN_PORT");
        String username = setting("CHAT_P2P_TURN_USERNAME");
        String password = setting("CHAT_P2P_TURN_PASSWORD");
        if (host.isBlank() && portValue.isBlank() && username.isBlank() && password.isBlank()) {
            return List.of();
        }
        if (host.isBlank() || portValue.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "Phai cau hinh day du CHAT_P2P_TURN_HOST, CHAT_P2P_TURN_PORT, "
                            + "CHAT_P2P_TURN_USERNAME va CHAT_P2P_TURN_PASSWORD");
        }
        try {
            int port = Integer.parseInt(portValue);
            return List.of(new TurnServerConfig(
                    new TransportAddress(host, port, Transport.UDP), username, password));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("CHAT_P2P_TURN_PORT khong hop le", error);
        }
    }

    private static String setting(String name) {
        String property = System.getProperty(name);
        return property == null ? System.getenv().getOrDefault(name, "") : property;
    }
}
