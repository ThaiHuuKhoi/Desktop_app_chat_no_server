package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.JoinRejectReason;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.datn.chatp2p.signaling.room.WrongPasswordLimiter;
import com.datn.chatp2p.signaling.auth.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.datn.chatp2p.signaling.limit.RequestRateLimiter;
import jakarta.annotation.PreDestroy;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String AUTH_TOKEN_ATTR = "authToken";
    private static final String JOINED_ONCE_ATTR = "joinedOnce";
    private static final String ADDRESS_ATTR = "clientAddress";

    /** Do dai toi da cac truong JOIN (truoc day khong gioi han, chi bi chan boi khung 64 KB). */
    static final int MAX_ROOM_ID_LENGTH = 200;
    static final int MAX_PEER_ID_LENGTH = 100;
    static final int MAX_USER_NAME_LENGTH = 64;
    /** Ket noi mo ma khong JOIN trong khoang nay bi dong (truoc day giu vo thoi han). */
    static final long JOIN_DEADLINE_MILLIS = 30_000;
    /**
     * Mac dinh so ket noi WebSocket dong thoi toi da tu 1 dia chi (IPv6 gop theo /64). Doi bang
     * {@code chat-p2p.ws.max-connections-per-address}. LUU Y khi trien khai sau reverse proxy: proxy PHAI
     * chuyen dung IP that (X-Forwarded-For), neu khong moi nguoi dung trung 1 dia chi va bi gioi han chung.
     */
    static final int MAX_CONNECTIONS_PER_ADDRESS = 32;
    /** Gioi han hang doi gui cho 1 session cham: qua thoi gian/kich thuoc thi dong session do. */
    static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private final RoomRegistry roomRegistry;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final boolean authenticationRequired;
    private final int maxConnectionsPerAddress;
    private final WrongPasswordLimiter wrongPasswordLimiter = new WrongPasswordLimiter();
    /** Moi session co 1 decorator gui an toan da luong, co gioi han hang doi (xem {@link #send}). */
    private final Map<String, WebSocketSession> outboundSessions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> connectionsByAddress = new ConcurrentHashMap<>();
    private final ScheduledExecutorService joinDeadlines = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ws-join-deadline");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public SignalingWebSocketHandler(RoomRegistry roomRegistry, ObjectMapper objectMapper, AuthService authService,
                                     @Value("${chat-p2p.auth.required:true}") boolean authenticationRequired,
                                     @Value("${chat-p2p.ws.max-connections-per-address:" + MAX_CONNECTIONS_PER_ADDRESS + "}")
                                     int maxConnectionsPerAddress) {
        this.roomRegistry = roomRegistry;
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.authenticationRequired = authenticationRequired;
        this.maxConnectionsPerAddress = maxConnectionsPerAddress;
    }

    /** Compatibility constructor for low-level relay tests that do not boot auth storage. */
    public SignalingWebSocketHandler(RoomRegistry roomRegistry, ObjectMapper objectMapper) {
        this.roomRegistry = roomRegistry;
        this.objectMapper = objectMapper;
        this.authService = null;
        this.authenticationRequired = false;
        this.maxConnectionsPerAddress = MAX_CONNECTIONS_PER_ADDRESS;
    }

    /**
     * Bao mat (chong giu ket noi): truoc day 1 ket noi WebSocket khong bao gio JOIN van duoc giu mai,
     * khong gioi han so ket noi moi dia chi - 1 may co the giu het slot ket noi cua server. Nay: toi da
     * {@value #MAX_CONNECTIONS_PER_ADDRESS} ket noi/dia chi, va phai JOIN trong
     * {@value #JOIN_DEADLINE_MILLIS} ms ke tu luc ket noi.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String address = addressKey(session);
        session.getAttributes().put(ADDRESS_ATTR, address);
        int open = connectionsByAddress.computeIfAbsent(address, ignored -> new AtomicInteger()).incrementAndGet();
        if (open > maxConnectionsPerAddress) {
            log.warn("Dong ket noi {} tu {}: vuot {} ket noi dong thoi", session.getId(), address,
                    maxConnectionsPerAddress);
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        joinDeadlines.schedule(() -> {
            if (session.isOpen() && !session.getAttributes().containsKey(JOINED_ONCE_ATTR)) {
                log.info("Dong ket noi {}: khong JOIN trong {} ms", session.getId(), JOIN_DEADLINE_MILLIS);
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            }
        }, JOIN_DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        joinDeadlines.shutdownNow();
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
            case OFFER, ANSWER, ICE_CANDIDATE -> relayToTargetPeer(session, signal);
            case PEER_JOINED, PEER_LEFT, PEER_LIST, JOIN_REJECTED ->
                    log.debug("Bo qua ban tin chi server moi duoc phat: {}", signal.getType());
        }
    }

    private void handleJoin(WebSocketSession session, SignalMessage signal) {
        // PeerSession dung Objects.requireNonNull cho ca 3 truong nay - phai tu
        // validate TRUOC khi goi constructor, neu khong 1 JOIN hop le ve JSON nhung
        // thieu truong (bug client hoac co y) se nem NullPointerException khong ai
        // bat, vi pham dung nguyen tac H.1 da ap dung cho loi parse JSON o tren.
        var authenticatedUser = signal.getAuthToken() != null && !signal.getAuthToken().isBlank()
                && authService != null
                ? authService.authenticate(signal.getAuthToken())
                : java.util.Optional.<com.datn.chatp2p.signaling.auth.AuthUser>empty();
        if (signal.getRoomId() == null || signal.getFromPeerId() == null || signal.getUserName() == null
                || (authenticationRequired && authenticatedUser.isEmpty())
                || (signal.getAuthToken() != null && !signal.getAuthToken().isBlank()
                && authenticatedUser.isEmpty())) {
            boolean missingField = signal.getRoomId() == null || signal.getFromPeerId() == null
                    || signal.getUserName() == null;
            boolean missingToken = signal.getAuthToken() == null || signal.getAuthToken().isBlank();
            String reason = missingField ? "thieu roomId/fromPeerId/userName"
                    : missingToken ? "server yeu cau dang nhap nhung JOIN khong co token"
                    : "token khong hop le hoac da het han";
            log.warn("Tu choi JOIN tu session {} (room={}): {}, token={}", session.getId(), signal.getRoomId(), reason,
                    com.datn.chatp2p.common.log.SecretLogging.reveal(signal.getAuthToken()));
            sendJoinRejected(session, signal.getRoomId(), missingField ? JoinRejectReason.INVALID_JOIN
                    : missingToken ? JoinRejectReason.AUTH_REQUIRED
                    : JoinRejectReason.AUTH_EXPIRED);
            return;
        }
        // Bao mat: gioi han do dai roomId/peerId/userName - truoc day chi bi chan boi khung 64 KB nen
        // moi phong co the giu ~64 KB chuoi tuy y, va GET /rooms tra ve tat ca ten phong do.
        if (signal.getRoomId().isBlank() || signal.getRoomId().length() > MAX_ROOM_ID_LENGTH
                || signal.getFromPeerId().isBlank() || signal.getFromPeerId().length() > MAX_PEER_ID_LENGTH
                || (authenticatedUser.isEmpty() && signal.getUserName().length() > MAX_USER_NAME_LENGTH)) {
            log.warn("Tu choi JOIN tu session {}: roomId/peerId/userName rong hoac qua dai", session.getId());
            sendJoinRejected(session, null, JoinRejectReason.INVALID_JOIN);
            return;
        }
        boolean privateRoom = signal.getRoomId().indexOf('~') >= 0;
        String clientKey = privateRoom ? clientKey(session) : null;
        if (privateRoom && wrongPasswordLimiter.isBlocked(clientKey)) {
            log.warn("Tu choi JOIN room={} tu {}: thu sai mat khau qua nhieu lan", signal.getRoomId(), clientKey);
            sendJoinRejected(session, signal.getRoomId(), JoinRejectReason.RATE_LIMITED);
            return;
        }
        authenticatedUser.ifPresent(user -> signal.setUserName(user.username()));

        // Neu session nay da JOIN truoc do ma chua LEAVE (client gui JOIN lan 2 -
        // bug hoac doi phong giua chung) - don dep entry CU truoc khi them entry
        // moi. Khong lam vay thi RoomRegistry se giu 2 entry cho CUNG 1 session,
        // nhung leaveBySession() (goi luc dong ket noi that su) chi tim va xoa
        // DUNG entry DAU TIEN khop session.getId() no gap - entry con lai se "mo
        // coi" vinh vien, khong ai bao PEER_LEFT cho phong cu.
        if (session.getAttributes().get(ROOM_ID_ATTR) != null) {
            removeAndNotify(session);
        }

        PeerSession self = new PeerSession(session, signal.getRoomId(), signal.getFromPeerId(), signal.getUserName());
        RoomRegistry.JoinResult joined = roomRegistry.tryJoin(self);
        if (!joined.accepted()) {
            if (JoinRejectReason.WRONG_PASSWORD.equals(joined.rejectReason())) {
                wrongPasswordLimiter.recordFailure(clientKey);
            }
            log.warn("Tu choi JOIN room={} peer={}: {}", self.getRoomId(), self.getPeerId(), joined.rejectReason());
            sendJoinRejected(session, self.getRoomId(), joined.rejectReason());
            return;
        }
        session.getAttributes().put(ROOM_ID_ATTR, self.getRoomId());
        session.getAttributes().put(PEER_ID_ATTR, self.getPeerId());
        session.getAttributes().put(JOINED_ONCE_ATTR, Boolean.TRUE);
        if (signal.getAuthToken() != null && !signal.getAuthToken().isBlank()) {
            session.getAttributes().put(AUTH_TOKEN_ATTR, signal.getAuthToken());
        }

        List<PeerSession> existingPeers = joined.existingPeers();

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
        outboundSessions.remove(session.getId());
        Object address = session.getAttributes().get(ADDRESS_ATTR);
        if (address instanceof String key) {
            AtomicInteger open = connectionsByAddress.get(key);
            if (open != null && open.decrementAndGet() <= 0) {
                connectionsByAddress.remove(key, open);
            }
        }
    }

    private void removeAndNotify(WebSocketSession session) {
        // Bao mat: xoa ngay rang buoc phong/peer/token cua session. Truoc day sau LEAVE (hoac JOIN lai
        // that bai) cac thuoc tinh nay van con, va isAuthorized() chi so voi chung - session da roi
        // phong van relay duoc OFFER/ICE vao phong cu duoi peerId cu.
        session.getAttributes().remove(ROOM_ID_ATTR);
        session.getAttributes().remove(PEER_ID_ATTR);
        session.getAttributes().remove(AUTH_TOKEN_ATTR);
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

    private void relayToTargetPeer(WebSocketSession sender, SignalMessage signal) {
        if (!isAuthorized(sender, signal)) {
            log.warn("Tu choi relay {} tu session {} do sender identity khong hop le",
                    signal.getType(), sender.getId());
            return;
        }
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
        // Bao mat: KHONG chuyen token phien cua nguoi gui cho peer dich. Client gan authToken vao moi
        // ban tin de server kiem tra lai; truoc day server relay nguyen object nen peer dich nhan duoc
        // token con han 12 gio cua nguoi gui va co the dung no de vao phong / dang xuat thay ho.
        signal.setAuthToken(null);
        send(target.get().getWebSocketSession(), signal);
    }

    private boolean isAuthorized(WebSocketSession session, SignalMessage signal) {
        Object joinedRoom = session.getAttributes().get(ROOM_ID_ATTR);
        Object joinedPeer = session.getAttributes().get(PEER_ID_ATTR);
        if (signal.getRoomId() == null || signal.getFromPeerId() == null
                || !signal.getRoomId().equals(joinedRoom) || !signal.getFromPeerId().equals(joinedPeer)) {
            return false;
        }
        // Rang buoc theo so phong hien tai, khong chi theo thuoc tinh session.
        if (!roomRegistry.isMember(session, signal.getRoomId(), signal.getFromPeerId())) {
            return false;
        }
        if (!authenticationRequired || authService == null) {
            return true;
        }
        Object token = session.getAttributes().get(AUTH_TOKEN_ATTR);
        return token instanceof String tokenValue && authService.authenticate(tokenValue).isPresent();
    }

    /** Bao client ly do bi tu choi JOIN ({@link JoinRejectReason}) - client hien thong bao tuong ung. */
    private void sendJoinRejected(WebSocketSession session, String roomId, String reason) {
        SignalMessage rejected = new SignalMessage();
        rejected.setType(SignalType.JOIN_REJECTED);
        rejected.setRoomId(roomId);
        rejected.setPayload(reason);
        send(session, rejected);
    }

    /** Khoa gioi han thu sai mat khau: dia chi IP cua client, IPv6 gop /64 (khong co thi dung id session). */
    private static String clientKey(WebSocketSession session) {
        java.net.InetSocketAddress remote = session.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? RequestRateLimiter.networkKey(remote.getAddress().getHostAddress())
                : "session:" + session.getId();
    }

    /** Khoa dem so ket noi: nhu {@link #clientKey} nhung khong co dia chi thi gop chung 1 khoa. */
    private static String addressKey(WebSocketSession session) {
        java.net.InetSocketAddress remote = session.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? RequestRateLimiter.networkKey(remote.getAddress().getHostAddress())
                : "unknown";
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("Dong session {} that bai: {}", session.getId(), e.getMessage());
        }
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
        // sendMessage() dong thoi tren CUNG 1 session (IllegalStateException
        // "TEXT_PARTIAL_WRITING" - phat hien qua WebSocketSignalingClientCapacityTest).
        // Ban cu khoa synchronized(session) roi ghi DONG BO: 1 peer ngung doc socket
        // lam MOI thread dang gui toi no (relay, PEER_JOINED/PEER_LEFT cua nguoi khac)
        // bi treo va xep hang tren khoa do. ConcurrentWebSocketSessionDecorator van
        // serialize ghi tren 1 session nhung cac thread khac chi xep ban tin vao hang
        // doi roi tra ve ngay; hang doi qua SEND_TIME_LIMIT/SEND_BUFFER_LIMIT thi
        // session cham bi dong thay vi lam nghen thread cua nguoi khac.
        WebSocketSession outbound = outboundSessions.computeIfAbsent(session.getId(),
                ignored -> new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT_BYTES));
        try {
            outbound.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (SessionLimitExceededException e) {
            log.warn("Dong session {}: nhan qua cham, hang doi gui vuot gioi han ({})", session.getId(), e.getMessage());
        } catch (IOException | IllegalStateException e) {
            log.warn("Gui ban tin {} toi session {} that bai (co the vua dong) - bo qua, khong anh huong peer khac: {}",
                    message.getType(), session.getId(), e.getMessage());
        }
    }
}
