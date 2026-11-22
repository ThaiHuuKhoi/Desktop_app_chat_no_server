package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.PeerVerificationState;

/**
 * 1 peer da ket noi trong phong, theo goc nhin cua giao dien.
 *
 * <p>Van tay tinh tren khoa ECDSA identity lau dai cua moi nguoi. Khoa nay
 * khac voi khoa ECDH tam thoi dung cho tung ket noi.
 *
 * @param localFingerprint  van tay khoa cua CHINH MINH trong ket noi voi peer nay;
 *                          {@code null} neu tang mang chua cung cap.
 * @param remoteFingerprint van tay khoa cua PEER NAY; {@code null} neu chua co.
 * @param verificationState ket qua xac minh chu ky identity tu dong.
 * @param account           server bao nguoi nay la tai khoan da dang nhap hay khach tu dat ten.
 */
public record SessionPeer(
        String peerId,
        String displayName,
        String localFingerprint,
        String remoteFingerprint,
        ConnectionKind connectionKind,
        PeerVerificationState verificationState,
        AccountKind account) {

    public SessionPeer(String peerId, String displayName, String localFingerprint, String remoteFingerprint,
                       ConnectionKind connectionKind, PeerVerificationState verificationState) {
        this(peerId, displayName, localFingerprint, remoteFingerprint, connectionKind, verificationState,
                AccountKind.UNKNOWN);
    }

    /** Nguoi nay vao phong bang tai khoan hay la khach - do signaling server xac nhan. */
    public enum AccountKind {
        /** Vao bang token tai khoan hop le: ten hien thi do server ep bang ten tai khoan. */
        ACCOUNT,
        /** Khach: ten tu dat, server khong xac nhan. */
        GUEST,
        /** Server cu khong bao thong tin nay. */
        UNKNOWN;

        public static AccountKind of(Boolean authenticated) {
            return authenticated == null ? UNKNOWN : authenticated ? ACCOUNT : GUEST;
        }
    }

    /** Duong truyen dang dung voi peer - hien tren header de nguoi dung biet. */
    public enum ConnectionKind {
        /** P2P truc tiep sau ICE. */
        DIRECT,
        /** Di vong qua TURN relay (van ma hoa dau-cuoi). */
        RELAY
    }
}
