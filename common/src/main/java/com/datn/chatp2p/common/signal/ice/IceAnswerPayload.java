package com.datn.chatp2p.common.signal.ice;

import java.util.List;

/**
 * Noi dung cua {@code SignalMessage.payload} khi {@code type == SignalType.ANSWER}
 * - Tai-lieu-ky-thuat.md Phan E.6.1. Cau truc giong het {@link IceOfferPayload},
 * tach rieng thanh 2 record de ro nghia ai gui (ben tra loi offer) va de mo rong
 * doc lap sau nay neu offer/answer can khac nhau.
 */
public record IceAnswerPayload(String ufrag, String password, List<String> candidates) {
}
