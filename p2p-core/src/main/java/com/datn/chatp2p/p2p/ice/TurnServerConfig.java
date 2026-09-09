package com.datn.chatp2p.p2p.ice;

import org.ice4j.TransportAddress;

/**
 * Thong tin 1 TURN server du phong - dung khi ICE khong tim duoc duong
 * truyen truc tiep (STUN khong du, vd ca 2 peer cung sau NAT doi xung -
 * Tai-lieu-ky-thuat.md Phan G.6/E.6.2). Khac {@link TransportAddress} STUN
 * (public, khong can xac thuc), TURN BAT BUOC co username/password: no relay
 * BANG THONG THAT cua server qua no (khong chi giup "do dia chi" nhu STUN),
 * nen phai xac thuc de tranh bi loi dung mien phi lam ban thong server
 * (RFC 5766 Muc 2.1/2.2 - "long-term credential mechanism").
 *
 * @param address  dia chi (host:port) cua TURN server.
 * @param username ten dang nhap TURN (long-term credential).
 * @param password mat khau TURN tuong ung.
 */
public record TurnServerConfig(TransportAddress address, String username, String password) {
}
