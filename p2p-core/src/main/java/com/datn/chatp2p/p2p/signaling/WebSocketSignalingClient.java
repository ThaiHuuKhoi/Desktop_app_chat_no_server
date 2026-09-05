package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Cai dat that cua {@link SignalingClient} bang {@code java.net.http.HttpClient}
 * (co san trong JDK, khong can them thu vien) - Tai-lieu-ky-thuat.md Phan E.6.4.
 *
 * <p>Chi lo dung 1 viec: (de)serialize {@link SignalMessage} sang/tu JSON va
 * chuyen qua lai qua WebSocket toi endpoint {@code /ws} cua {@code signaling-server}.
 * Khong biet gi ve noi dung ben trong {@code payload} (SDP/ICE credentials) - do
 * la viec cua lop goi no (vi du bo dieu phoi ICE se tu serialize/deserialize
 * {@code IceOfferPayload}/{@code IceAnswerPayload} roi dua chuoi JSON do vao
 * {@link #sendOffer}/{@link #sendAnswer} nhu mot {@code payload} co hoi).
 *
 * <p><b>Tu dong reconnect</b> (Tai-lieu-ky-thuat.md Phan H.3): neu WebSocket dong
 * BAT THUONG (mat mang, server sap) SAU KHI da ket noi thanh cong it nhat 1 lan,
 * tu dong thu ket noi lai voi backoff tang dan (1s, 2s, 4s... toi da 30s), roi tu
 * gui lai JOIN de tham gia lai dung phong - KHONG can lop goi (RoomSession) tu xu
 * ly. Chi goi {@link #connect} lan DAU (khi con chua ket noi lan nao) moi nem loi
 * ngay neu that bai - dung hanh vi cu, khong thay doi hop dong hien co. Dang ky
 * {@link #onConnectionStateChanged} de biet luc nao dang "Mat ket noi, dang thu
 * lai..." (vi du hien len UI) - day la method rieng cua lop nay, KHONG thuoc
 * {@link SignalingClient}, vi khong phai moi cai dat (vd fake dung cho test) can
 * co khai niem reconnect.
 */
public final class WebSocketSignalingClient implements SignalingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration INITIAL_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofSeconds(30);

    /** Trang thai ket noi toi signaling server - dung cho {@link #onConnectionStateChanged}. */
    public enum ConnectionState {
        CONNECTED,
        /** Vua mat ket noi bat thuong, dang tu dong thu lai voi backoff. */
        RECONNECTING,
        /** Nguoi dung/lop goi chu dong {@link #disconnect()} - se KHONG tu ket noi lai. */
        DISCONNECTED
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<SignalType, List<Consumer<SignalMessage>>> handlers = new EnumMap<>(SignalType.class);
    private final StringBuilder incomingTextBuffer = new StringBuilder();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "signaling-client-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    private volatile WebSocket webSocket;
    private volatile String serverUri;
    private volatile String roomId;
    private volatile String peerId;
    private volatile String userName;
    private volatile boolean intentionallyDisconnected;
    private volatile Duration nextReconnectDelay = INITIAL_RECONNECT_DELAY;
    private volatile Consumer<ConnectionState> connectionStateHandler;

    @Override
    public void connect(String serverUri, String roomId, String peerId, String userName) {
        this.serverUri = serverUri;
        this.roomId = roomId;
        this.peerId = peerId;
        this.userName = userName;
        this.intentionallyDisconnected = false;
        this.nextReconnectDelay = INITIAL_RECONNECT_DELAY;

        // Lan dau: de loi nem thang ra ngoai nhu cu (khong tu retry) - chi
        // KHI DA KET NOI DUOC IT NHAT 1 LAN, mat ket noi sau do moi tu dong
        // reconnect (xem handleUnexpectedDisconnect).
        establishConnection();
    }

    /** Dang ky nhan thong bao khi trang thai ket noi doi (vd de hien UI "Mat ket noi, dang thu lai..."). */
    public void onConnectionStateChanged(Consumer<ConnectionState> handler) {
        this.connectionStateHandler = handler;
    }

    @Override
    public void onPeerJoined(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.PEER_JOINED, handler);
    }

    @Override
    public void onPeerLeft(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.PEER_LEFT, handler);
    }

    @Override
    public void onPeerList(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.PEER_LIST, handler);
    }

    @Override
    public void onOffer(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.OFFER, handler);
    }

    @Override
    public void onAnswer(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.ANSWER, handler);
    }

    @Override
    public void onIceCandidate(Consumer<SignalMessage> handler) {
        registerHandler(SignalType.ICE_CANDIDATE, handler);
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
        intentionallyDisconnected = true;
        reconnectExecutor.shutdownNow(); // huy moi lan thu reconnect dang cho lich

        WebSocket socket = this.webSocket;
        if (socket == null) {
            notifyState(ConnectionState.DISCONNECTED);
            return;
        }
        SignalMessage leave = new SignalMessage();
        leave.setType(SignalType.LEAVE);
        leave.setRoomId(roomId);
        leave.setFromPeerId(peerId);
        sendMessage(leave);

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "leave")
                .exceptionally(e -> null)
                .join();
        notifyState(ConnectionState.DISCONNECTED);
    }

    /** Mo ket noi that (dung chung boi lan connect() dau va moi lan thu reconnect). */
    private void establishConnection() {
        URI wsUri = URI.create(serverUri + "/ws");
        CompletableFuture<WebSocket> connecting = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(wsUri, new SignalingWebSocketListener());

        WebSocket socket;
        try {
            socket = connecting.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Qua thoi gian cho ket noi toi signaling server: " + wsUri, e);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the ket noi toi signaling server: " + wsUri, e);
        }

        this.webSocket = socket;
        this.nextReconnectDelay = INITIAL_RECONNECT_DELAY; // reset backoff sau khi ket noi lai thanh cong
        notifyState(ConnectionState.CONNECTED);
        sendMessage(SignalMessage.join(roomId, peerId, userName));
    }

    /** Goi tu Listener khi WebSocket dong/loi BAT THUONG (khong phai do chinh minh goi disconnect()). */
    private void handleUnexpectedDisconnect() {
        if (intentionallyDisconnected) {
            return;
        }
        notifyState(ConnectionState.RECONNECTING);
        scheduleReconnectAttempt();
    }

    private void scheduleReconnectAttempt() {
        Duration delay = nextReconnectDelay;
        try {
            reconnectExecutor.schedule(this::attemptReconnect, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // reconnectExecutor da bi shutdown (disconnect() vua duoc goi giua chung) - bo qua, dung retry.
            return;
        }
        Duration doubled = delay.multipliedBy(2);
        nextReconnectDelay = doubled.compareTo(MAX_RECONNECT_DELAY) > 0 ? MAX_RECONNECT_DELAY : doubled;
    }

    private void attemptReconnect() {
        if (intentionallyDisconnected) {
            return;
        }
        try {
            establishConnection();
        } catch (RuntimeException e) {
            // Van chua ket noi lai duoc (server con sap, mang con mat...) - thu tiep voi backoff da tang.
            scheduleReconnectAttempt();
        }
    }

    private void notifyState(ConnectionState state) {
        Consumer<ConnectionState> handler = connectionStateHandler;
        if (handler != null) {
            handler.accept(state);
        }
    }

    private void registerHandler(SignalType type, Consumer<SignalMessage> handler) {
        handlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    private void sendTargeted(SignalType type, String toPeerId, String payload) {
        SignalMessage message = new SignalMessage();
        message.setType(type);
        message.setRoomId(roomId);
        message.setFromPeerId(peerId);
        message.setToPeerId(toPeerId);
        message.setPayload(payload);
        sendMessage(message);
    }

    private void sendMessage(SignalMessage message) {
        WebSocket socket = this.webSocket;
        if (socket == null) {
            throw new IllegalStateException("Chua ket noi toi signaling server - goi connect() truoc");
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            socket.sendText(json, true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong serialize duoc SignalMessage: " + message, e);
        }
    }

    private void dispatch(SignalMessage message) {
        if (message.getType() == null) {
            return;
        }
        List<Consumer<SignalMessage>> forType = handlers.get(message.getType());
        if (forType == null) {
            return;
        }
        for (Consumer<SignalMessage> handler : forType) {
            handler.accept(message);
        }
    }

    /**
     * WebSocket co the chia 1 ban tin thanh nhieu frame (tham so {@code last}
     * bao khi nao ket thuc) - phai gop lai truoc khi parse JSON, khong duoc
     * gia dinh moi lan goi {@code onText} la 1 ban tin day du.
     */
    private final class SignalingWebSocketListener implements WebSocket.Listener {

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incomingTextBuffer.append(data);
            webSocket.request(1);

            if (last) {
                String json = incomingTextBuffer.toString();
                incomingTextBuffer.setLength(0);
                try {
                    dispatch(objectMapper.readValue(json, SignalMessage.class));
                } catch (IOException e) {
                    // Ban tin loi dinh dang - bo qua, khong lam sap ca ket noi
                    // (dung nguyen tac xu ly loi o Tai-lieu-ky-thuat.md Phan H.1).
                }
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            // Server/mang dong ket noi (khong phai minh chu dong goi disconnect(),
            // truong hop do da duoc danh dau intentionallyDisconnected=true truoc
            // khi goi sendClose - xem handleUnexpectedDisconnect kiem tra co flag nay).
            handleUnexpectedDisconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            handleUnexpectedDisconnect();
        }
    }
}
