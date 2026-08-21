package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;

import java.util.function.Consumer;

/**
 * Khung cai dat {@link SignalingClient} qua WebSocket toi {@code signaling-server}
 * (endpoint {@code /ws}, xem module {@code signaling-server}).
 *
 * <p><b>TODO (Thanh vien A, tuan 7-9, cung luc voi {@code P2pDataChannel}):</b>
 * <ol>
 *   <li>Mo ket noi bang {@code java.net.http.HttpClient.newWebSocketBuilder()}
 *       (co san trong JDK, khong can them thu vien) toi {@code serverUri + "/ws"}.</li>
 *   <li>Serialize/deserialize {@link SignalMessage} sang/tu JSON (Jackson, da
 *       co san qua module {@code common} + dependency o {@code p2p-core}) va
 *       goi dung handler theo {@link SignalMessage#getType()}.</li>
 *   <li>Gui JOIN ngay sau khi ket noi thanh cong; gui LEAVE (hoac de server tu
 *       phat hien qua ws close) khi {@link #disconnect()} duoc goi.</li>
 * </ol>
 *
 * <p>Cho toi khi hoan thien, cac method deu nem {@link UnsupportedOperationException}.
 */
public class WebSocketSignalingClient implements SignalingClient {

    private static final String NOT_IMPLEMENTED =
            "WebSocketSignalingClient chua duoc cai dat - xem TODO tuan 7-9.";

    @Override
    public void connect(String serverUri, String roomId, String peerId, String userName) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onPeerJoined(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onPeerLeft(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onPeerList(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onOffer(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onAnswer(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void onIceCandidate(Consumer<SignalMessage> handler) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void sendOffer(String toPeerId, String sdp) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void sendAnswer(String toPeerId, String sdp) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void sendIceCandidate(String toPeerId, String candidate) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void disconnect() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
