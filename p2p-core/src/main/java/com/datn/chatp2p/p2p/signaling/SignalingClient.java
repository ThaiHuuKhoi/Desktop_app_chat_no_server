package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;

import java.util.function.Consumer;

/**
 * Hop dong cho client ket noi toi {@code signaling-server} de tham gia phong
 * va trao doi SDP/ICE candidate voi cac peer khac, truoc khi ket noi P2P
 * truc tiep duoc thiet lap (De-cuong-Chat-P2P-Java.md muc 6, giai doan 3-4).
 *
 * <p>Day la hop dong on dinh de {@link com.datn.chatp2p.p2p.channel.P2pDataChannel}
 * (Thanh vien A) dua vao khi cai dat ICE that; xem {@link WebSocketSignalingClient}
 * cho cai dat that bang {@code java.net.http.HttpClient}.
 */
public interface SignalingClient {

    /** Ket noi toi signaling server va tham gia mot phong. */
    void connect(String serverUri, String roomId, String peerId, String userName);

    /** Goi khi co peer moi tham gia phong. */
    void onPeerJoined(Consumer<SignalMessage> handler);

    /** Goi khi mot peer roi phong. */
    void onPeerLeft(Consumer<SignalMessage> handler);

    /** Goi khi nhan duoc danh sach day du cac peer dang co trong phong. */
    void onPeerList(Consumer<SignalMessage> handler);

    /** Goi khi nhan duoc SDP offer tu mot peer khac (relay qua server). */
    void onOffer(Consumer<SignalMessage> handler);

    /** Goi khi nhan duoc SDP answer tu mot peer khac (relay qua server). */
    void onAnswer(Consumer<SignalMessage> handler);

    /** Goi khi nhan duoc ICE candidate tu mot peer khac (relay qua server). */
    void onIceCandidate(Consumer<SignalMessage> handler);

    /** Gui SDP offer toi mot peer cu the trong cung phong. */
    void sendOffer(String toPeerId, String sdp);

    /** Gui SDP answer toi mot peer cu the trong cung phong. */
    void sendAnswer(String toPeerId, String sdp);

    /** Gui ICE candidate toi mot peer cu the trong cung phong. */
    void sendIceCandidate(String toPeerId, String candidate);

    /** Roi phong va dong ket noi toi signaling server. */
    void disconnect();
}
