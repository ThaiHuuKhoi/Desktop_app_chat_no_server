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
    private String authToken;
    private String payload;
    /**
     * Chi server dat, trong PEER_JOINED: {@code true} = nguoi nay vao bang token tai khoan hop le (ten
     * hien thi do server ep bang ten tai khoan), {@code false} = khach tu dat ten, {@code null} = server
     * cu khong bao thong tin nay.
     */
    private Boolean authenticated;
    /** Chi server dat, trong PEER_JOINED: khoa danh tinh (Base64 X.509) da dang ky cua tai khoan, null voi khach. */
    private String identityKey;
    /**
     * Client gui trong JOIN: phien ban giao thuc no hieu. Client cu khong gui (null) -&gt; server KHONG gui
     * cac truong moi ({@link #authenticated}, {@link #identityKey}) cho no, vi client cu dung ObjectMapper
     * mac dinh, gap truong la la loi parse ca ban tin.
     */
    private Integer protocolVersion;

    /** Tu phien ban nay client hieu {@code authenticated} / {@code identityKey} trong PEER_LIST / PEER_JOINED. */
    public static final int ACCOUNT_INFO_PROTOCOL_VERSION = 2;
    private List<PeerInfo> peers = new ArrayList<>();

    public SignalMessage() {
    }

    public static SignalMessage join(String roomId, String peerId, String userName) {
        SignalMessage m = new SignalMessage();
        m.type = SignalType.JOIN;
        m.roomId = roomId;
        m.fromPeerId = peerId;
        m.userName = userName;
        m.protocolVersion = ACCOUNT_INFO_PROTOCOL_VERSION;
        return m;
    }

    public Integer getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(Integer protocolVersion) {
        this.protocolVersion = protocolVersion;
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

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(String identityKey) {
        this.identityKey = identityKey;
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
        /** Xem {@link SignalMessage#getAuthenticated()}. */
        private Boolean authenticated;
        /** Xem {@link SignalMessage#getIdentityKey()}. */
        private String identityKey;

        public PeerInfo() {
        }

        public PeerInfo(String peerId, String userName) {
            this.peerId = peerId;
            this.userName = userName;
        }

        public PeerInfo(String peerId, String userName, Boolean authenticated, String identityKey) {
            this(peerId, userName);
            this.authenticated = authenticated;
            this.identityKey = identityKey;
        }

        public Boolean getAuthenticated() {
            return authenticated;
        }

        public void setAuthenticated(Boolean authenticated) {
            this.authenticated = authenticated;
        }

        public String getIdentityKey() {
            return identityKey;
        }

        public void setIdentityKey(String identityKey) {
            this.identityKey = identityKey;
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
