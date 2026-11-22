package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.log.SecretLogging;
import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.IdentityPayload;
import com.datn.chatp2p.common.protocol.MessageAckPayload;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.common.protocol.MessageEditPayload;
import com.datn.chatp2p.common.protocol.MessageReadPayload;
import com.datn.chatp2p.common.protocol.ReactionPayload;
import com.datn.chatp2p.common.protocol.MessageTranscriptPayload;
import com.datn.chatp2p.common.protocol.MessageTranscriptPayload.HistoryMessage;
import com.datn.chatp2p.common.protocol.RoomProofPayload;
import com.datn.chatp2p.common.protocol.TypingStatusPayload;
import com.datn.chatp2p.crypto.RoomSecret;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.RoomSession;
import com.datn.chatp2p.p2p.ice.TurnServerConfig;
import com.datn.chatp2p.p2p.signaling.WebSocketSignalingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * ChatSession chay tren signaling + ICE/P2P that.
 *
 * <p><b>Cong vao phong co mat khau</b>: sau khi trao khoa ECDH, peer chi duoc "nhan vao"
 * ({@link #admittedPeers}) khi gui {@code ROOM_PROOF} hop le (xem {@link RoomSecret}). Truoc do
 * moi ban tin cua peer bi bo qua va giao dien khong thay peer. Phong cong khai thi nhan vao ngay.
 *
 * <p><b>Lich su cho nguoi moi</b>: khi xac thuc xong danh tinh 1 peer, gui cho peer do lich su
 * chat NHOM ({@link GroupHistory}, toi da 200 tin, khong gom DM) qua {@code MESSAGE_TRANSCRIPT},
 * chia goi + ACK/gui lai. Ca 2 ben deu gui; ben nhan loai trung theo id, nen nguoi moi (lich su
 * rong) chi nhan, con 2 nguoi cu ket noi lai thi bu cho nhau phan con thieu.
 */
public final class P2pChatSession implements ChatSession {
    private static final Logger LOG = Logger.getLogger(P2pChatSession.class.getName());

    private static final List<TransportAddress> STUN_SERVERS = List.of(
            new TransportAddress("stun.l.google.com", 19302, Transport.UDP));
    private static final Properties APPLICATION_PROPERTIES = loadApplicationProperties();
    private static final List<TurnServerConfig> TURN_SERVERS = loadTurnServers();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** So lan gui lai 1 tin nhan / goi lich su chua duoc ACK, va khoang cach giua cac lan. */
    private static final int MESSAGE_MAX_RETRIES = 6;
    private static final long MESSAGE_RETRY_INTERVAL_MILLIS = 500;
    /** Gui lai ROOM_PROOF / PEER_IDENTITY (UDP co the mat goi, khong co ACK rieng). */
    private static final long[] HANDSHAKE_RESEND_DELAYS_MILLIS = {400, 1200, 2500};

    private final RoomConfig config;
    private final WebSocketSignalingClient signalingClient;
    private final String selfPeerId = UUID.randomUUID().toString();
    private final PeerIdentityService identityService = new PeerIdentityService();
    /** null = phong cong khai. */
    private final RoomSecret roomSecret;
    private final Map<String, SessionPeer> knownPeers = new ConcurrentHashMap<>();
    private final Map<String, String> verifiedRemoteFingerprints = new ConcurrentHashMap<>();
    private final Set<String> admittedPeers = ConcurrentHashMap.newKeySet();
    private final Set<String> rejectedPeers = ConcurrentHashMap.newKeySet();
    private final Set<String> historySentTo = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> historyAcceptedFrom = new ConcurrentHashMap<>();
    private final GroupHistory history = new GroupHistory();

    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "p2p-session-retry");
                thread.setDaemon(true);
                return thread;
            });
    private final MessageDeliveryTracker deliveryTracker = new MessageDeliveryTracker(
            retryExecutor, MESSAGE_MAX_RETRIES, MESSAGE_RETRY_INTERVAL_MILLIS,
            (peerId, messageId) -> {
                LOG.warning(() -> "Het luot gui lai: id=" + shortId(messageId) + " toi " + who(peerId)
                        + " khong nhan duoc ACK sau " + MESSAGE_MAX_RETRIES + " lan");
                notifyDeliveryFailed(messageId, peerId);
                notifyConnectionFailed(peerId, new IOException(
                        "tin nhắn chưa tới nơi (không nhận được xác nhận sau "
                                + MESSAGE_MAX_RETRIES + " lần gửi lại)"));
            });
    private final RoomSession roomSession;
    /** "ten" hoac "ten~the" (the suy ra tu mat khau bang PBKDF2) - khoa luu lich su tren may. */
    private final String serverRoomId;
    private final FileTransferManager fileTransfers;

    private volatile ChatSessionListener listener;
    private volatile boolean left;

    public P2pChatSession(RoomConfig config) {
        this.config = config;
        this.roomSecret = config.isPasswordProtected()
                ? RoomSecret.derive(config.roomName(), config.roomPassword())
                : null;
        // Phong co mat khau: server chi thay "ten~the", nguoi khac mat khau roi vao phong khac.
        this.serverRoomId = roomSecret == null
                ? config.roomName()
                : config.roomName() + RoomConfig.PRIVATE_ROOM_SEPARATOR + roomSecret.serverRoomTag();
        LOG.info(() -> "Tao phien: toi=" + config.userName() + " (" + shortId(selfPeerId) + ")"
                + ", phong=" + config.roomName()
                + (roomSecret != null ? " [co mat khau, roomId tren server=" + serverRoomId + "]" : " [cong khai]")
                + ", server=" + config.serverUri()
                + (roomSecret != null ? ", mat khau phong=" + SecretLogging.reveal(config.roomPassword()) : "")
                + ", dang nhap=" + (config.authToken() != null && !config.authToken().isBlank())
                + (config.authToken() != null && !config.authToken().isBlank()
                        ? ", token=" + SecretLogging.reveal(config.authToken()) : ""));
        // TURN: uu tien cau hinh cuc bo (client.properties / bien moi truong - tien khi test); khong co thi
        // xin tai khoan TAM THOI tu server (GET /turn-credentials) - nguoi dung khong phai cau hinh gi.
        List<TurnServerConfig> turnServers = TURN_SERVERS.isEmpty()
                ? new AuthClient().turnServers(config.serverUri())
                : TURN_SERVERS;
        LOG.info(() -> "TURN: " + (!TURN_SERVERS.isEmpty() ? "dung cau hinh cuc bo (" + TURN_SERVERS.size() + " server)"
                : turnServers.isEmpty() ? "khong co (chi ket noi truc tiep)"
                : "dung tai khoan tam thoi tu server " + turnServers.get(0).address()));
        this.signalingClient = new WebSocketSignalingClient();
        this.signalingClient.setAuthToken(config.authToken());
        this.roomSession = new RoomSession(
                serverRoomId,
                selfPeerId,
                config.userName(),
                signalingClient,
                STUN_SERVERS,
                turnServers);
        this.fileTransfers = new FileTransferManager(
                (peerId, type, namespace, payload) -> roomSession.sendTo(peerId, type, namespace, payload),
                this::notifyFileTransfer);
    }

    @Override
    public String selfPeerId() {
        return selfPeerId;
    }

    @Override
    public String storageKey() {
        return serverRoomId;
    }

    @Override
    public void start(ChatSessionListener listener) {
        this.listener = listener;
        LOG.info(() -> "Vao phong: gui JOIN toi signaling server " + config.serverUri());
        roomSession.onPeerJoined(this::handleConnectionReady);
        roomSession.onPeerLeft(this::notifyPeerLeft);
        roomSession.onConnectionFailed(this::notifyConnectionFailed);
        roomSession.onJoinRejected(this::notifyJoinRejected);
        roomSession.onEnvelope(EnvelopeType.ROOM_PROOF, this::handleRoomProof);
        roomSession.onEnvelope(EnvelopeType.MESSAGE, admittedOnly(this::handleMessage));
        roomSession.onEnvelope(EnvelopeType.MESSAGE_ACK, admittedOnly(this::handleMessageAck));
        roomSession.onEnvelope(EnvelopeType.MESSAGE_TRANSCRIPT, admittedOnly(this::handleTranscript));
        roomSession.onEnvelope(EnvelopeType.TYPING_STATUS_CHANGE, admittedOnly(this::handleTypingStatus));
        roomSession.onEnvelope(EnvelopeType.PEER_IDENTITY, admittedOnly(this::handlePeerIdentity));
        roomSession.onEnvelope(EnvelopeType.MESSAGE_READ, admittedOnly(this::handleMessageRead));
        roomSession.onEnvelope(EnvelopeType.MESSAGE_REACTION, admittedOnly(this::handleReaction));
        roomSession.onEnvelope(EnvelopeType.MESSAGE_EDIT, admittedOnly(this::handleMessageEdit));
        // Truyen file cung chi nhan tu peer da qua cong mat khau.
        for (EnvelopeType type : List.of(EnvelopeType.FILE_OFFER, EnvelopeType.FILE_ANSWER,
                EnvelopeType.FILE_CHUNK, EnvelopeType.FILE_ACK, EnvelopeType.FILE_CANCEL)) {
            roomSession.onEnvelope(type, admittedOnly(fileTransfers::handleEnvelope));
        }

        try {
            roomSession.join(config.serverUri());
        } catch (RuntimeException error) {
            LOG.warning("P2P session signaling join failed: " + error.getMessage());
            notifyConnectionFailed("", error);
        }
    }

    // ------------------------------------------------------------ gui tin

    @Override
    public void sendGroupMessage(ChatMessage message) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the gui tin nhan");
        }
        // Dung id/thoi gian cua chinh ChatMessage: lich su chia se giua cac peer loai trung theo id.
        MessagePayload payload = new MessagePayload(
                message.getId(), message.getAuthorId(), message.getText(), message.getTimeSent(), message.getReplyToId());
        history.add(new HistoryMessage(message.getId(), selfPeerId, config.userName(),
                message.getText(), message.getTimeSent()));
        List<String> recipients = admittedPeerIds();
        LOG.info(() -> "Gui tin NHOM id=" + shortId(message.getId()) + " " + SecretLogging.text(message.getText())
                + " toi " + recipients.size() + " nguoi"
                + (recipients.isEmpty() ? " - phong chua co ai, chi luu vao lich su" : ""));
        notifyMessageSent(message.getId(), recipients);
        for (String peerId : recipients) {
            try {
                sendMessageReliably(peerId, null, payload);
            } catch (RuntimeException error) {
                // 1 peer loi khong duoc chan viec gui toi cac peer con lai.
                notifyDeliveryFailed(message.getId(), peerId);
                notifyConnectionFailed(peerId, error);
            }
        }
    }

    @Override
    public void sendDirectMessage(String peerId, ChatMessage message) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the gui tin nhan");
        }
        if (!admittedPeers.contains(peerId)) {
            throw new IllegalArgumentException("Người này chưa vào phòng hoặc đã rời phòng");
        }
        MessagePayload payload = new MessagePayload(
                message.getId(), message.getAuthorId(), message.getText(), message.getTimeSent(), message.getReplyToId());
        LOG.info(() -> "Gui tin RIENG id=" + shortId(message.getId()) + " " + SecretLogging.text(message.getText())
                + " toi " + who(peerId));
        notifyMessageSent(message.getId(), List.of(peerId));
        sendMessageReliably(peerId, EnvelopeNamespace.DIRECT_MESSAGE, payload);
    }

    private void notifyMessageSent(String messageId, List<String> recipients) {
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onMessageSent(messageId, List.copyOf(recipients));
        }
    }

    private void notifyDeliveryFailed(String messageId, String peerId) {
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onMessageDeliveryFailed(messageId, peerId);
        }
    }

    @Override
    public void sendTypingStatus(String directPeerId, boolean typing) {
        if (left) {
            return;
        }
        TypingStatusPayload payload = new TypingStatusPayload(typing);
        LOG.fine(() -> "Gui dang go=" + typing + (directPeerId == null ? " (nhom)" : " (rieng toi " + who(directPeerId) + ")"));
        // Best-effort: khong ACK, khong gui lai, khong bao loi len UI (tranh lam phien vi
        // 1 tin hieu "dang go" bi mat). Ben gui nhac lai dinh ky, ben nhan tu het han.
        if (directPeerId != null) {
            if (admittedPeers.contains(directPeerId)) {
                sendQuietly(directPeerId, EnvelopeType.TYPING_STATUS_CHANGE, EnvelopeNamespace.DIRECT_MESSAGE, payload);
            }
            return;
        }
        for (String peerId : admittedPeerIds()) {
            sendQuietly(peerId, EnvelopeType.TYPING_STATUS_CHANGE, null, payload);
        }
    }

    @Override
    public long maxFileSize() {
        return fileTransfers.limits().maxFileSize();
    }

    @Override
    public void sendFile(String peerId, Path file, boolean direct) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the gui file");
        }
        if (!admittedPeers.contains(peerId)) {
            throw new IllegalArgumentException("Người này chưa vào phòng hoặc đã rời phòng");
        }
        fileTransfers.sendFile(peerId, file, direct);
    }

    @Override
    public void acceptFile(String fileId, Path directory) {
        fileTransfers.acceptFile(fileId, directory);
    }

    @Override
    public void rejectFile(String fileId) {
        fileTransfers.rejectFile(fileId);
    }

    @Override
    public void cancelFile(String fileId) {
        fileTransfers.cancelFile(fileId);
    }

    @Override
    public void leave() {
        if (left) {
            return;
        }
        // Bao ben kia huy cac lan truyen file TRUOC khi dong ket noi
        fileTransfers.close();
        left = true;
        LOG.info(() -> "Roi phong " + config.roomName() + " - dong " + roomSession.getPeerIds().size()
                + " ket noi P2P va ngat signaling");
        retryExecutor.shutdownNow();
        roomSession.leave();
    }

    private void sendMessageReliably(String peerId, String namespace, MessagePayload payload) {
        sendReliably(peerId, payload.id(),
                () -> roomSession.sendTo(peerId, EnvelopeType.MESSAGE, namespace, payload));
    }

    /**
     * Gui 1 ban tin va tu gui lai cho toi khi nhan MESSAGE_ACK(id) (xem
     * {@link MessageDeliveryTracker}). Dang ky theo doi TRUOC khi gui lan dau de khong bo lo
     * ACK toi qua nhanh.
     */
    private void sendReliably(String peerId, String id, Runnable send) {
        deliveryTracker.track(peerId, id, send);
        try {
            send.run();
        } catch (RuntimeException error) {
            deliveryTracker.acknowledge(peerId, id);
            throw error;
        }
    }

    private void sendQuietly(String peerId, EnvelopeType type, String namespace, Object payload) {
        try {
            roomSession.sendTo(peerId, type, namespace, payload);
        } catch (RuntimeException error) {
            LOG.fine(() -> "Gui " + type + " toi " + peerId + " that bai: " + error.getMessage());
        }
    }

    private List<String> admittedPeerIds() {
        return roomSession.getPeerIds().stream().filter(admittedPeers::contains).toList();
    }

    // ------------------------------------------------------------ vao phong / mat khau

    /** ECDH xong voi 1 peer: phong cong khai -> nhan vao ngay; phong co mat khau -> gui bang chung, cho ben kia. */
    private void handleConnectionReady(PeerConnection connection) {
        if (left) {
            return;
        }
        LOG.info(() -> "Kenh ma hoa da san sang voi " + who(connection.getPeerId())
                + " (ECDH xong, AES-256-GCM)");
        if (roomSecret == null) {
            admit(connection);
            return;
        }
        LOG.info(() -> "Phong co mat khau: gui ROOM_PROOF toi " + who(connection.getPeerId())
                + ", cho bang chung cua ho");
        Runnable sendProof = () -> {
            byte[] proof = roomSecret.proof(selfPeerId,
                    connection.getLocalEcdhPublicKey(), connection.getRemoteEcdhPublicKey());
            sendQuietlyOn(connection, EnvelopeType.ROOM_PROOF,
                    new RoomProofPayload(Base64.getEncoder().encodeToString(proof)));
        };
        sendProof.run();
        repeatWhileConnected(connection, sendProof);
    }

    private void handleRoomProof(String peerId, Envelope envelope) {
        if (roomSecret == null || admittedPeers.contains(peerId) || rejectedPeers.contains(peerId)) {
            return; // ban gui lai cua bang chung da xu ly
        }
        PeerConnection connection = roomSession.getPeerConnection(peerId);
        if (connection == null || connection.getRemoteEcdhPublicKey() == null) {
            return;
        }
        boolean valid;
        try {
            RoomProofPayload payload = OBJECT_MAPPER.readValue(envelope.payload(), RoomProofPayload.class);
            valid = roomSecret.verifyProof(Base64.getDecoder().decode(payload.proof()), peerId,
                    connection.getRemoteEcdhPublicKey(), connection.getLocalEcdhPublicKey());
        } catch (IOException | IllegalArgumentException | NullPointerException error) {
            valid = false;
        }
        if (valid) {
            LOG.info(() -> "ROOM_PROOF cua " + who(peerId) + " hop le - ho biet dung mat khau phong");
            admit(connection);
        } else {
            rejectedPeers.add(peerId);
            LOG.warning("Tu choi " + who(peerId) + ": ROOM_PROOF khong hop le (sai mat khau phong)");
            notifyConnectionFailed(peerId, new SecurityException("người này không có đúng mật khẩu phòng"));
        }
    }

    /** Peer da qua cong (phong cong khai, hoac co bang chung mat khau hop le): hien len giao dien. */
    private void admit(PeerConnection connection) {
        String peerId = connection.getPeerId();
        if (!admittedPeers.add(peerId)) {
            return;
        }
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }
        String remoteFingerprint = verifiedRemoteFingerprints.get(peerId);
        boolean relayed = roomSession.isRelayed(peerId).orElse(false);
        LOG.info(() -> "Duong truyen toi " + who(peerId) + ": " + (relayed ? "qua TURN relay" : "truc tiep"));
        SessionPeer peer = new SessionPeer(
                peerId,
                connection.getCustomUsername() == null
                        ? "Người dùng " + peerId
                        : connection.getCustomUsername(),
                identityService.localFingerprint(),
                remoteFingerprint,
                relayed ? SessionPeer.ConnectionKind.RELAY : SessionPeer.ConnectionKind.DIRECT,
                remoteFingerprint == null
                        ? PeerVerificationState.VERIFYING
                        : PeerVerificationState.VERIFIED);
        knownPeers.put(peerId, peer);
        LOG.info(() -> "Nguoi moi trong phong: " + who(peerId) + " - hien len giao dien, gui PEER_IDENTITY");
        currentListener.onPeerJoined(peer);
        Runnable sendIdentity = () -> sendIdentity(connection);
        sendIdentity.run();
        repeatWhileConnected(connection, sendIdentity);
    }

    private void repeatWhileConnected(PeerConnection connection, Runnable action) {
        for (long delay : HANDSHAKE_RESEND_DELAYS_MILLIS) {
            try {
                retryExecutor.schedule(() -> {
                    if (!left && roomSession.getPeerConnection(connection.getPeerId()) == connection) {
                        action.run();
                    }
                }, delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                return; // da roi phong
            }
        }
    }

    private void sendQuietlyOn(PeerConnection connection, EnvelopeType type, Object payload) {
        try {
            connection.send(type, payload);
        } catch (RuntimeException error) {
            LOG.fine(() -> "Gui " + type + " toi " + connection.getPeerId() + " that bai: " + error.getMessage());
        }
    }

    /** Bo qua ban tin cua peer chua qua cong mat khau. */
    private java.util.function.BiConsumer<String, Envelope> admittedOnly(
            java.util.function.BiConsumer<String, Envelope> handler) {
        return (peerId, envelope) -> {
            if (admittedPeers.contains(peerId)) {
                handler.accept(peerId, envelope);
            }
        };
    }

    // ------------------------------------------------------------ nhan tin

    private void handleMessage(String peerId, Envelope envelope) {
        MessagePayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(envelope.payload(), MessagePayload.class);
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE hong tu " + peerId);
            return;
        }
        if (payload.id() == null || payload.text() == null) {
            return;
        }
        // Luon ACK (ca ban trung) - ACK truoc co the da bi mat tren duong truyen.
        sendQuietly(peerId, EnvelopeType.MESSAGE_ACK, null, new MessageAckPayload(payload.id()));
        // Bao mat: gioi han do dai nhu duong sua tin / lich su. Truoc day tin truc tiep chi bi chan o
        // 1 MiB cua kenh UDP; 1 tin ~750k ky tu kieu "*a *a ..." lam bo dung Markdown chay O(n^2) tren
        // luong JavaFX va treo cua so nguoi nhan. (Van ACK de ben gui khong gui lai mai.)
        if (payload.text().length() > GroupHistory.MAX_TEXT_CHARS) {
            LOG.warning(() -> "Bo qua tin qua dai (" + payload.text().length() + " ky tu) tu " + who(peerId));
            return;
        }
        if (!deliveryTracker.markReceived(peerId, payload.id())) {
            LOG.fine(() -> "Bo qua ban trung id=" + shortId(payload.id()) + " tu " + who(peerId) + " (da ACK lai)");
            return; // ban gui lai cua tin nhan da hien thi
        }
        LOG.info(() -> "Nhan tin " + (EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace()) ? "RIENG" : "NHOM")
                + " id=" + shortId(payload.id()) + " " + SecretLogging.text(payload.text()) + " tu " + who(peerId)
                + " - da gui ACK");
        long now = System.currentTimeMillis();
        // Tac gia LUON la peer gui ket noi nay (khong tin authorId trong payload); dong ho cua
        // peer co the lech - khong cho thoi gian o tuong lai.
        long timeSent = Math.min(payload.timeSent(), now);
        ChatMessage message = new ChatMessage(payload.id(), peerId, payload.text(), timeSent, now, payload.replyToId());

        boolean direct = EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace());
        if (!direct) {
            history.add(new HistoryMessage(payload.id(), peerId, displayName(peerId), payload.text(), timeSent));
        }
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            if (direct) {
                currentListener.onDirectMessageReceived(peerId, message);
            } else {
                currentListener.onMessageReceived(message);
            }
        }
    }

    private void handleMessageAck(String peerId, Envelope envelope) {
        try {
            MessageAckPayload ack = OBJECT_MAPPER.readValue(envelope.payload(), MessageAckPayload.class);
            LOG.fine(() -> "Nhan ACK id=" + shortId(ack.messageId()) + " tu " + who(peerId));
            if (deliveryTracker.acknowledge(peerId, ack.messageId())) {
                ChatSessionListener currentListener = listener;
                if (currentListener != null && !left) {
                    currentListener.onMessageDelivered(ack.messageId(), peerId);
                }
            }
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_ACK hong tu " + peerId);
        }
    }

    /** Toi da so id trong 1 ban tin MESSAGE_READ (goi UDP nho, va chong ban tin rac qua lon). */
    private static final int MAX_READ_IDS_PER_PACKET = 20;

    @Override
    public void sendReadReceipt(String authorPeerId, List<String> messageIds, boolean direct) {
        if (left || messageIds.isEmpty() || !admittedPeers.contains(authorPeerId)) {
            return;
        }
        LOG.fine(() -> "Bao da xem " + messageIds.size() + " tin cho " + who(authorPeerId));
        // Best-effort: mat thi tin chi hien "Da nhan" thay vi "Da xem" - khong gui lai.
        for (int from = 0; from < messageIds.size(); from += MAX_READ_IDS_PER_PACKET) {
            List<String> chunk = List.copyOf(messageIds.subList(from, Math.min(messageIds.size(), from + MAX_READ_IDS_PER_PACKET)));
            sendQuietly(authorPeerId, EnvelopeType.MESSAGE_READ,
                    direct ? EnvelopeNamespace.DIRECT_MESSAGE : null, new MessageReadPayload(chunk));
        }
    }

    @Override
    public void sendReaction(String directPeerId, String messageId, String emoji, boolean added) {
        if (left || messageId == null || !ReactionPayload.ALLOWED.contains(emoji)) {
            return;
        }
        ReactionPayload payload = new ReactionPayload(messageId, emoji, added);
        LOG.info(() -> (added ? "Them" : "Bo") + " cam xuc tren tin id=" + shortId(messageId)
                + (directPeerId == null ? " (nhom)" : " (rieng voi " + who(directPeerId) + ")"));
        if (directPeerId != null) {
            if (admittedPeers.contains(directPeerId)) {
                sendQuietly(directPeerId, EnvelopeType.MESSAGE_REACTION, EnvelopeNamespace.DIRECT_MESSAGE, payload);
            }
            return;
        }
        for (String peerId : admittedPeerIds()) {
            sendQuietly(peerId, EnvelopeType.MESSAGE_REACTION, null, payload);
        }
    }

    private void handleMessageRead(String peerId, Envelope envelope) {
        MessageReadPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(envelope.payload(), MessageReadPayload.class);
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_READ hong tu " + peerId);
            return;
        }
        if (payload.messageIds() == null) {
            return;
        }
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }
        payload.messageIds().stream()
                .filter(java.util.Objects::nonNull)
                .limit(MAX_READ_IDS_PER_PACKET)
                .forEach(id -> currentListener.onMessageRead(id, peerId));
    }

    @Override
    public void editMessage(String directPeerId, String messageId, String newText) {
        if (left) {
            throw new IllegalStateException("Da roi phong, khong the sua tin");
        }
        if (newText != null && newText.length() > GroupHistory.MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("Tin nhắn quá dài");
        }
        MessageEditPayload payload = new MessageEditPayload(UUID.randomUUID().toString(), messageId, newText, newText == null);
        LOG.info(() -> (newText == null ? "Xoa" : "Sua") + " tin id=" + shortId(messageId)
                + (directPeerId == null ? " (nhom)" : " (rieng voi " + who(directPeerId) + ")"));
        if (directPeerId != null) {
            if (!admittedPeers.contains(directPeerId)) {
                throw new IllegalArgumentException("Người này chưa vào phòng hoặc đã rời phòng");
            }
            sendReliably(directPeerId, payload.editId(), () -> roomSession.sendTo(
                    directPeerId, EnvelopeType.MESSAGE_EDIT, EnvelopeNamespace.DIRECT_MESSAGE, payload));
            return;
        }
        // Lich su nhom chia se cho nguoi vao sau cung phai theo ban moi (hoac khong con tin da xoa).
        if (newText == null) {
            history.remove(messageId, selfPeerId);
        } else {
            history.edit(messageId, selfPeerId, newText);
        }
        for (String peerId : admittedPeerIds()) {
            try {
                sendReliably(peerId, payload.editId(),
                        () -> roomSession.sendTo(peerId, EnvelopeType.MESSAGE_EDIT, null, payload));
            } catch (RuntimeException error) {
                notifyConnectionFailed(peerId, error);
            }
        }
    }

    private void handleMessageEdit(String peerId, Envelope envelope) {
        MessageEditPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(envelope.payload(), MessageEditPayload.class);
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_EDIT hong tu " + peerId);
            return;
        }
        if (payload.editId() == null || payload.messageId() == null
                || (!payload.deleted() && (payload.text() == null || payload.text().length() > GroupHistory.MAX_TEXT_CHARS))) {
            return;
        }
        sendQuietly(peerId, EnvelopeType.MESSAGE_ACK, null, new MessageAckPayload(payload.editId()));
        if (!deliveryTracker.markReceived(peerId, payload.editId())) {
            return; // ban gui lai
        }
        boolean direct = EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace());
        // Chi tac gia moi sua / xoa duoc: GroupHistory chi doi tin co authorId == nguoi gui ban tin nay.
        if (!direct) {
            if (payload.deleted()) {
                history.remove(payload.messageId(), peerId);
            } else {
                history.edit(payload.messageId(), peerId, payload.text());
            }
        }
        LOG.info(() -> who(peerId) + (payload.deleted() ? " xoa" : " sua") + " tin id=" + shortId(payload.messageId()));
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onMessageEdited(payload.messageId(), peerId, payload.deleted() ? null : payload.text());
        }
    }

    private void handleReaction(String peerId, Envelope envelope) {
        ReactionPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(envelope.payload(), ReactionPayload.class);
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_REACTION hong tu " + peerId);
            return;
        }
        // Chi nhan cam xuc trong bo co dinh - khong de nguoi khac chen chu tuy y vao giao dien.
        if (payload.messageId() == null || !ReactionPayload.ALLOWED.contains(payload.emoji())) {
            return;
        }
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onReaction(payload.messageId(), peerId, payload.emoji(), payload.added());
        }
    }

    private void handleTypingStatus(String peerId, Envelope envelope) {
        ChatSessionListener currentListener = listener;
        if (currentListener == null || left) {
            return;
        }
        try {
            TypingStatusPayload payload = OBJECT_MAPPER.readValue(envelope.payload(), TypingStatusPayload.class);
            LOG.fine(() -> "Nhan dang go=" + payload.typing() + " tu " + who(peerId)
                    + (EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace()) ? " (rieng)" : " (nhom)"));
            currentListener.onPeerTyping(peerId,
                    EnvelopeNamespace.DIRECT_MESSAGE.equals(envelope.namespace()), payload.typing());
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua TYPING_STATUS_CHANGE hong tu " + peerId);
        }
    }

    // ------------------------------------------------------------ lich su (backfill)

    /** Gui lich su nhom cho peer vua xac thuc danh tinh xong (moi peer 1 lan moi ket noi). */
    private void sendHistoryTo(String peerId) {
        if (!historySentTo.add(peerId)) {
            return;
        }
        List<HistoryMessage> snapshot = history.snapshot();
        List<List<HistoryMessage>> batches = GroupHistory.batches(snapshot);
        LOG.info(() -> snapshot.isEmpty()
                ? "Lich su nhom rong - khong co gi de gui cho " + who(peerId)
                : "Gui lich su cho " + who(peerId) + ": " + snapshot.size() + " tin trong " + batches.size() + " goi");
        for (List<HistoryMessage> batch : batches) {
            MessageTranscriptPayload payload = new MessageTranscriptPayload(UUID.randomUUID().toString(), batch);
            try {
                sendReliably(peerId, payload.batchId(),
                        () -> roomSession.sendTo(peerId, EnvelopeType.MESSAGE_TRANSCRIPT, payload));
            } catch (RuntimeException error) {
                LOG.fine(() -> "Gui lich su toi " + peerId + " that bai: " + error.getMessage());
                return;
            }
        }
    }

    private void handleTranscript(String peerId, Envelope envelope) {
        if (!verifiedRemoteFingerprints.containsKey(peerId)) {
            // Chua xac thuc danh tinh nguoi chia se: KHONG ACK de ho gui lai sau khi xac thuc xong.
            LOG.fine(() -> "Tam bo qua goi lich su tu " + who(peerId) + " - chua xac thuc danh tinh, cho ho gui lai");
            return;
        }
        MessageTranscriptPayload payload;
        try {
            payload = OBJECT_MAPPER.readValue(envelope.payload(), MessageTranscriptPayload.class);
        } catch (IOException error) {
            LOG.fine(() -> "Bo qua MESSAGE_TRANSCRIPT hong tu " + peerId);
            return;
        }
        if (payload.batchId() == null) {
            return;
        }
        sendQuietly(peerId, EnvelopeType.MESSAGE_ACK, null, new MessageAckPayload(payload.batchId()));
        if (!deliveryTracker.markReceived(peerId, payload.batchId()) || payload.messages() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        List<ChatMessage> added = new ArrayList<>();
        Map<String, String> authorNames = new HashMap<>();
        for (HistoryMessage incoming : payload.messages()) {
            // Moi peer chi duoc chia se toi da MAX_MESSAGES tin moi ket noi (chong spam lich su gia).
            if (historyAcceptedFrom.getOrDefault(peerId, 0) >= GroupHistory.MAX_MESSAGES) {
                break;
            }
            if (!GroupHistory.isAcceptable(incoming)) {
                continue;
            }
            // Bao mat: nguoi chia se khong duoc gan tin cho CHINH MINH (nguoi nhan). Lich su chia se
            // khong xac minh duoc tac gia; neu chap nhan authorId == selfPeerId, tin gia se hien nhu tin
            // minh viet va bi luu lai thanh "tin cua toi" trong lich su tren may. Tin that cua minh da
            // co san trong history nen khong mat gi.
            if (selfPeerId.equals(incoming.authorId())) {
                continue;
            }
            HistoryMessage normalized = new HistoryMessage(incoming.id(), incoming.authorId(),
                    incoming.authorName(), incoming.text(), Math.min(incoming.timeSent(), now));
            if (history.add(normalized)) {
                historyAcceptedFrom.merge(peerId, 1, Integer::sum);
                added.add(new ChatMessage(normalized.id(), normalized.authorId(), normalized.text(),
                        normalized.timeSent(), now));
                if (normalized.authorName() != null && !normalized.authorName().isBlank()) {
                    authorNames.putIfAbsent(normalized.authorId(), normalized.authorName());
                }
            }
        }
        int total = payload.messages().size();
        LOG.info(() -> "Nhan goi lich su tu " + who(peerId) + ": " + total + " tin, " + added.size()
                + " tin moi" + (added.size() < total ? " (" + (total - added.size()) + " da co/khong hop le)" : ""));
        if (SecretLogging.enabled()) {
            for (ChatMessage message : added) {
                LOG.info(() -> "  lich su: [" + authorNames.getOrDefault(message.getAuthorId(), shortId(message.getAuthorId()))
                        + "] " + SecretLogging.text(message.getText()));
            }
        }
        ChatSessionListener currentListener = listener;
        if (!added.isEmpty() && currentListener != null && !left) {
            currentListener.onHistoryReceived(peerId, added, authorNames);
        }
    }

    // ------------------------------------------------------------ danh tinh

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
                LOG.warning("Chu ky PEER_IDENTITY cua " + who(peerId)
                        + " KHONG hop le (gia mao hoac khoa ECDH bi trao - nghi ngo MITM)");
                notifyConnectionFailed(peerId, new SecurityException("Identity peer khong hop le"));
                return;
            }

            String remoteFingerprint = verification.fingerprint();
            boolean firstVerification = verifiedRemoteFingerprints.put(peerId, remoteFingerprint) == null;
            if (firstVerification) {
                LOG.info(() -> "Da xac thuc danh tinh " + who(peerId) + ", fingerprint "
                        + remoteFingerprint.substring(0, Math.min(19, remoteFingerprint.length())) + "...");
                sendHistoryTo(peerId);
            }
            SessionPeer current = knownPeers.get(peerId);
            if (current == null || current.verificationState() == PeerVerificationState.VERIFIED) {
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

    // ------------------------------------------------------------ roi phong / loi

    private void notifyFileTransfer(FileTransferStatus status) {
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onFileTransferUpdated(status);
        }
    }

    private void notifyPeerLeft(String peerId) {
        String name = who(peerId);
        LOG.info(() -> name + " da roi phong" + (admittedPeers.contains(peerId) ? "" : " (chua tung vao duoc phong)"));
        deliveryTracker.forgetPeer(peerId);
        fileTransfers.onPeerLeft(peerId);
        knownPeers.remove(peerId);
        verifiedRemoteFingerprints.remove(peerId);
        historySentTo.remove(peerId);
        historyAcceptedFrom.remove(peerId);
        rejectedPeers.remove(peerId);
        boolean wasAdmitted = admittedPeers.remove(peerId);
        ChatSessionListener currentListener = listener;
        if (wasAdmitted && currentListener != null && !left) {
            currentListener.onPeerLeft(peerId);
        }
    }

    private void notifyJoinRejected(String reason) {
        LOG.warning(() -> "Server tu choi cho vao phong " + config.roomName() + ": " + reason);
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            currentListener.onJoinRejected(reason);
        }
    }

    private void notifyConnectionFailed(String peerId, Throwable error) {
        LOG.warning(() -> "Loi voi " + (peerId == null || peerId.isEmpty() ? "signaling server" : who(peerId))
                + ": " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        ChatSessionListener currentListener = listener;
        if (currentListener != null && !left) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            currentListener.onPeerConnectionFailed(peerId, reason);
        }
    }

    private String displayName(String peerId) {
        SessionPeer peer = knownPeers.get(peerId);
        return peer == null ? null : peer.displayName();
    }

    /** "Ten (8 ky tu dau peerId)" cho log - de phan biet nhieu client cung ten khi test. */
    private String who(String peerId) {
        String name = displayName(peerId);
        if (name == null) {
            PeerConnection connection = roomSession.getPeerConnection(peerId);
            name = connection == null ? null : connection.getCustomUsername();
        }
        return (name == null ? "peer" : name) + " (" + shortId(peerId) + ")";
    }

    private static String shortId(String id) {
        return id == null ? "?" : id.substring(0, Math.min(8, id.length()));
    }

    // ------------------------------------------------------------ cau hinh TURN

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
