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
 * <p><b>Chua xu ly (de bo sung sau):</b> tu dong reconnect khi mat ket noi
 * (Tai-lieu-ky-thuat.md Phan H.3) - hien tai neu WebSocket dong bat thuong,
 * client se khong tu ket noi lai.
 */
public final class WebSocketSignalingClient implements SignalingClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<SignalType, List<Consumer<SignalMessage>>> handlers = new EnumMap<>(SignalType.class);
    private final StringBuilder incomingTextBuffer = new StringBuilder();

    private volatile WebSocket webSocket;
    private volatile String roomId;
    private volatile String peerId;

    @Override
    public void connect(String serverUri, String roomId, String peerId, String userName) {
        this.roomId = roomId;
        this.peerId = peerId;

        URI wsUri = URI.create(serverUri + "/ws");
        CompletableFuture<WebSocket> connecting = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(wsUri, new SignalingWebSocketListener());

        try {
            this.webSocket = connecting.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Qua thoi gian cho ket noi toi signaling server: " + wsUri, e);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the ket noi toi signaling server: " + wsUri, e);
        }

        sendMessage(SignalMessage.join(roomId, peerId, userName));
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
        WebSocket socket = this.webSocket;
        if (socket == null) {
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
        public void onError(WebSocket webSocket, Throwable error) {
            // TODO (Thanh vien A): tu dong reconnect voi backoff tang dan +
            // bao UI "Mat ket noi may chu, dang thu lai..." - Phan H.3.
        }
    }
}
