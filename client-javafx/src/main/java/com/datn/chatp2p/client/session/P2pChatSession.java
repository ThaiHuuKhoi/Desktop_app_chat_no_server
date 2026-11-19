package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.IdentityPayload;
import com.datn.chatp2p.common.protocol.MessageAckPayload;
import com.datn.chatp2p.common.protocol.TypingStatusPayload;
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
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * ChatSession backed by the real signaling and ICE/P2P stack.
 */
public final class P2pChatSession implements ChatSession {
    private static final Logger LOG = Logger.getLogger(P2pChatSession.class.getName());

    private static final List<TransportAddress> STUN_SERVERS = List.of(
            new TransportAddress("stun.l.google.com", 19302, Transport.UDP));
    private static final Properties APPLICATION_PROPERTIES = loadApplicationProperties();
    private static final List<TurnServerConfig> TURN_SERVERS = loadTurnServers();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RoomConfig config;
    private final WebSocketSignalingClient signalingClient;
    private final String selfPeerId = UUID.randomUUID().toString();
    private final PeerIdentityService identityService = new PeerIdentityService();
    private final Map<String, SessionPeer> knownPeers = new ConcurrentHashMap<>();
    private final Map<String, String> verifiedRemoteFingerprints = new ConcurrentHashMap<>();
    /** So lan gui lai 1 tin nhan chua duoc ACK, va khoang cach giua cac lan. */
    private static final int MESSAGE_MAX_RETRIES = 6;
    private static final long MESSAGE_RETRY_INTERVAL_MILLIS = 500;

    private final ScheduledExecutorService identityRetryExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "p2p-session-retry");
                thread.setDaemon(true);
                return thread;
            });
    private final MessageDeliveryTracker deliveryTracker = new MessageDeliveryTracker(
            identityRetryExecutor, MESSAGE_MAX_RETRIES, MESSAGE_RETRY_INTERVAL_MILLIS,
            (peerId, messageId) -> notifyConnectionFailed(peerId, new IOException(
                    "tin nhắn chưa tới nơi (không nhận được xác nhận sau "
                            + MESSAGE_MAX_RETRIES + " lần gửi lại)")));
    private final RoomSession roomSession;

    private volatile ChatSessionListener listener;
    private volatile boolean left;

    public P2pChatSession(RoomConfig config) {
        this.config = config;
        LOG.info(() -> "P2P session create: peerId=" + selfPeerId
                + ", signalingServer=" + config.serverUri()
                + ", room=" + config.roomName()
                + ", stunServers=" + STUN_SERVERS);
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
        LOG.info(() -> "P2P session start: peerId=" + selfPeerId);
        roomSession.onPeerJoined(this::handlePeerJoined);
        roomSession.onPeerLeft(peerId -> notifyPeerLeft(peerId));
        roomSession.onConnectionFailed((peerId, error) ->
                notifyConnectionFailed(peerId, error));
        roomSession.onEnvelope(EnvelopeType.MESSAGE, this::handleMessage);
        roomSession.onEnvelope(EnvelopeType.MESSAGE_ACK, this::handleMessageAck);
        roomSession.onEnvelope(EnvelopeType.TYPING_STATUS_CHANGE, this::handleTypingStatus);
        roomSession.onEnvelope(EnvelopeType.PEER_IDENTITY, this::handlePeerIdentity);

        try {
            roomSession.join(config.serverUri());
        } catch (RuntimeException error) {
            LOG.warning("P2P session signaling join failed: " + error.getMessage());
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
        for (String peerId : roomSession.getPeerIds()) {
            try {
                sendReliably(peerId, null, payload);
            } catch (RuntimeException error) {
                // 1 peer loi khong duoc chan viec gui toi cac peer con lai.
                notifyConnectionFailed(peerId, error);
            }
        }
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
        sendReliably(peerId, EnvelopeNamespace.DIRECT_MESSAGE, payload);
    }

    @Override
    public void sendTypingStatus(String directPeerId, boolean typing) {
        if (left) {
            return;
        }
        TypingStatusPayload payload = new TypingStatusPayload(typing);
        // Best-effort: khong ACK, khong gui lai, khong bao loi len UI (tranh lam phien vi
        // 1 tin hieu "dang go" bi mat). Ben gui nhac lai dinh ky, ben nhan tu het han.
        if (directPeerId != null) {
            sendTypingQuietly(directPeerId, EnvelopeNamespace.DIRECT_MESSAGE, payload);
            return;
        }
        for (String peerId : roomSession.getPeerIds()) {
            sendTypingQuietly(peerId, null, payload);
        }
    }

    private void sendTypingQuietly(String peerId, String namespace, TypingStatusPayload payload) {
        try {
            roomSession.sendTo(peerId, EnvelopeType.TYPING_STATUS_CHANGE, namespace, payload);
        } catch (RuntimeException error) {
            LOG.fine(() -> "Gui TYPING_STATUS_CHANGE toi " + peerId + " that bai: " + error.getMessage());
        }
    }

    private void handleTypingStatus(String peerId, Envelope envelope) {
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }
        try {
            TypingStatusPayload payload = OBJECT_MAPPER.readValue(envelope.payload(), TypingStatusPayload.class);
            currentListener.onPeerTyping(peerId,
                    EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace()), payload.typing());
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua TYPING_STATUS_CHANGE hong tu " + peerId);
        }
    }

    /**
     * Gui 1 MESSAGE va tu gui lai cho toi khi nhan MESSAGE_ACK (xem
     * {@link MessageDeliveryTracker}). Dang ky theo doi TRUOC khi gui lan dau de
     * khong bo lo ACK toi qua nhanh.
     */
    private void sendReliably(String peerId, String namespace, MessagePayload payload) {
        Runnable send = () -> roomSession.sendTo(peerId, EnvelopeType.MESSAGE, namespace, payload);
        deliveryTracker.track(peerId, payload.id(), send);
        try {
            send.run();
        } catch (RuntimeException error) {
            deliveryTracker.acknowledge(peerId, payload.id());
            throw error;
        }
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
        // Luon ACK (ca ban trung) - ACK truoc co the da bi mat tren duong truyen.
        try {
            roomSession.sendTo(peerId, EnvelopeType.MESSAGE_ACK, new MessageAckPayload(payload.id()));
        } catch (RuntimeException error) {
            LOG.fine(() -> "Gui MESSAGE_ACK toi " + peerId + " that bai: " + error.getMessage());
        }
        if (!deliveryTracker.markReceived(peerId, payload.id())) {
            return; // ban gui lai cua tin nhan da hien thi
        }
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

    private void handleMessageAck(String peerId, Envelope envelope) {
        try {
            MessageAckPayload ack = OBJECT_MAPPER.readValue(envelope.payload(), MessageAckPayload.class);
            deliveryTracker.acknowledge(peerId, ack.messageId());
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_ACK hong tu " + peerId);
        }
    }

    private void sendIdentity(PeerConnection connection) {
        try {
            IdentityPayload payload = identityService.createPayload(
                    config.roomName(), selfPeerId,
                    connection.getLocalEcdhPublicKey(), connection.getRemoteEcdhPublicKey());
            connection.send(EnvelopeType.PEER_IDENTITY, payload);
        } catch (RuntimeException error) {
            notifyConnectionFailed(connection.getPeerId(), error);
        }
    }

    private void handlePeerIdentity(String peerId, Envelope envelope) {
        try {
            IdentityPayload payload = OBJECT_MAPPER.readValue(envelope.payload(), IdentityPayload.class);
            PeerConnection connection = roomSession.getPeerConnection(peerId);
            if (connection == null || connection.getRemoteEcdhPublicKey() == null) {
                return; // peer vua roi phong / ket noi da bi thay the
            }
            PeerIdentityService.Verification verification = identityService.verify(
                    payload, config.roomName(), peerId,
                    connection.getRemoteEcdhPublicKey(), connection.getLocalEcdhPublicKey());
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
        deliveryTracker.forgetPeer(peerId);
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
        if (property != null) {
            return property.trim();
        }
        String environment = System.getenv(name);
        if (environment != null) {
            return environment.trim();
        }
        String applicationKey = name.toLowerCase(Locale.ROOT).replace('_', '.');
        return APPLICATION_PROPERTIES.getProperty(applicationKey, "").trim();
    }

    /**
     * Doc cau hinh: {@code application.properties} trong jar (chi gia tri mac dinh,
     * KHONG chua bi mat), roi ghi de bang file cuc bo
     * {@code ~/.chat-p2p-java/client.properties} (noi dat TURN credential, khong
     * nam trong repo/jar). Thu tu uu tien khi doc: -D system property, bien moi
     * truong, file cuc bo, file trong jar.
     */
    private static Properties loadApplicationProperties() {
        Properties properties = new Properties();
        try (var input = P2pChatSession.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Khong the doc application.properties", error);
        }
        java.nio.file.Path localConfig = java.nio.file.Path.of(
                System.getProperty("user.home"), ".chat-p2p-java", "client.properties");
        if (java.nio.file.Files.isRegularFile(localConfig)) {
            try (var reader = java.nio.file.Files.newBufferedReader(localConfig)) {
                properties.load(reader);
            } catch (IOException error) {
                throw new IllegalStateException("Khong the doc " + localConfig, error);
            }
        }
        return properties;
    }
}
