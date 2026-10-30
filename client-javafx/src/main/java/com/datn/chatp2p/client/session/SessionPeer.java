package com.datn.chatp2p.client.session;

/**
 * 1 peer da ket noi trong phong, theo goc nhin cua giao dien.
 *
 * <p>Van tay tinh tren khoa ECDH cua TUNG ket noi (moi cap peer co cap khoa
 * rieng), nen ca {@code localFingerprint} lan {@code remoteFingerprint} deu
 * gan voi peer nay: 2 nguoi doc cho nhau 2 day nay de phat hien ke xen giua.
 *
 * @param localFingerprint  van tay khoa cua CHINH MINH trong ket noi voi peer nay;
 *                          {@code null} neu tang mang chua cung cap.
 * @param remoteFingerprint van tay khoa cua PEER NAY; {@code null} neu chua co.
 */
public record SessionPeer(
        String peerId,
        String displayName,
        String localFingerprint,
        String remoteFingerprint,
        ConnectionKind connectionKind) {

    /** Duong truyen dang dung voi peer - hien tren header de nguoi dung biet. */
    public enum ConnectionKind {
        /** Peer gia lap trong cung tien trinh (che do demo). */
        DEMO,
        /** P2P truc tiep sau ICE. */
        DIRECT,
        /** Di vong qua TURN relay (van ma hoa dau-cuoi). */
        RELAY
    }
}
