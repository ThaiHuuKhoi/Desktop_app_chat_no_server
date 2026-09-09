package com.datn.chatp2p.common.signal.ice;

import java.util.List;

/**
 * Noi dung cua {@code SignalMessage.payload} (da serialize sang JSON) khi
 * {@code SignalMessage.type == SignalType.OFFER} - Tai-lieu-ky-thuat.md Phan E.6.1.
 *
 * <p>Ben goi (peer thay ban khac da co san qua PEER_LIST) tao Agent ice4j,
 * gather toan bo candidate cuc bo (khong dung trickle ICE trong ban dau nay),
 * roi gui payload nay cho ben kia qua {@code SignalingClient.sendOffer}.
 *
 * <p>Moi phan tu trong {@code candidates} la 1 dong dinh dang chuan RFC 5245/8839
 * (vi du {@code "candidate:1 1 udp 2130706431 192.168.1.5 54321 typ host"}) -
 * chinh la {@code LocalCandidate.toString()} cua ice4j, khong tu bia dinh dang rieng.
 */
public record IceOfferPayload(String ufrag, String password, List<String> candidates) {
}
