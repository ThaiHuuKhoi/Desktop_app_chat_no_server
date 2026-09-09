package com.datn.chatp2p.p2p.ice;

/**
 * So lieu do hiệu nang cho 1 phien {@link IceP2pConnectionEstablisher} da
 * thiet lap xong - dung cho Tai-lieu-ky-thuat.md Phan F.1 ("Thoi gian thiet
 * lap ket noi P2P", "Ti le ket noi truc tiep thanh cong / phai relay").
 *
 * @param establishmentMillis thoi gian (mili-giay) tu luc tao
 *                             {@link IceP2pConnectionEstablisher} (nghia la
 *                             luc RoomSession quyet dinh bat dau ket noi voi
 *                             peer nay) den luc {@code IceProcessingState.COMPLETED}.
 *                             <b>Khong bao gom</b> thoi gian JOIN/OFFER/ANSWER
 *                             di qua signaling server - chi tinh rieng phan
 *                             ICE (gather + connectivity check).
 * @param usingRelay           {@code true} neu candidate pair duoc chon o BAT KY
 *                             phia nao la {@code RELAYED_CANDIDATE} (nghia la du
 *                             lieu se di qua TURN server thay vi truc tiep/qua
 *                             STUN-punched NAT) - tren localhost (khong co TURN
 *                             cau hinh) luon la {@code false}.
 */
public record IceConnectionStats(long establishmentMillis, boolean usingRelay) {
}
