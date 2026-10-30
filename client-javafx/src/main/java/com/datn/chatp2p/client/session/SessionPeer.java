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
 */
public record SessionPeer(
        String peerId,
        String displayName,
        String localFingerprint,
        String remoteFingerprint,
        ConnectionKind connectionKind,
        PeerVerificationState verificationState) {

    /** Duong truyen dang dung voi peer - hien tren header de nguoi dung biet. */
    public enum ConnectionKind {
        /** P2P truc tiep sau ICE. */
        DIRECT,
        /** Di vong qua TURN relay (van ma hoa dau-cuoi). */
        RELAY
    }
}
