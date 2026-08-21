package com.datn.chatp2p.common.signal;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO cho giao thuc signaling, (de)serialize sang/tu JSON boi
 * {@code signaling-server} (Jackson) va {@code SignalingClient} phia client.
 *
 * <p>La mot POJO thuan (khong annotation Jackson) de module {@code common}
 * khong phu thuoc Jackson - ObjectMapper mac dinh van doc/ghi duoc nho co
 * constructor rong + getter/setter chuan.
 *
 * <p>{@code payload} la chuoi co hoi (vi du SDP/ICE candidate da duoc serialize
 * o tang tren) - server khong bao gio parse hay doc noi dung ben trong.
 */
public class SignalMessage {

    private SignalType type;
    private String roomId;
    private String fromPeerId;
    private String toPeerId;
    private String userName;
    private String payload;
    private List<PeerInfo> peers = new ArrayList<>();

    public SignalMessage() {
    }

    public static SignalMessage join(String roomId, String peerId, String userName) {
        SignalMessage m = new SignalMessage();
        m.type = SignalType.JOIN;
        m.roomId = roomId;
        m.fromPeerId = peerId;
        m.userName = userName;
        return m;
    }

    public SignalType getType() {
        return type;
    }

    public void setType(SignalType type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getFromPeerId() {
        return fromPeerId;
    }

    public void setFromPeerId(String fromPeerId) {
        this.fromPeerId = fromPeerId;
    }

    public String getToPeerId() {
        return toPeerId;
    }

    public void setToPeerId(String toPeerId) {
        this.toPeerId = toPeerId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public List<PeerInfo> getPeers() {
        return peers;
    }

    public void setPeers(List<PeerInfo> peers) {
        this.peers = peers;
    }

    /** Thong tin toi gian ve mot peer, dung trong ban tin PEER_LIST/PEER_JOINED. */
    public static class PeerInfo {
        private String peerId;
        private String userName;

        public PeerInfo() {
        }

        public PeerInfo(String peerId, String userName) {
            this.peerId = peerId;
            this.userName = userName;
        }

        public String getPeerId() {
            return peerId;
        }

        public void setPeerId(String peerId) {
            this.peerId = peerId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

    @Override
    public String toString() {
        return "SignalMessage{type=%s, roomId=%s, fromPeerId=%s, toPeerId=%s, userName=%s}"
                .formatted(type, roomId, fromPeerId, toPeerId, userName);
    }
}
