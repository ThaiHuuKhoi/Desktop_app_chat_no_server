package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@link EnvelopeType#FILE_ANSWER} - nguoi nhan tra loi 1
 * {@link FileOfferPayload} (docs/Thong-nhat-giao-thuc-truyen-file.md D1).
 * Ben gui chi bat dau gui chunk khi {@code accepted = true}.
 *
 * @param reason ly do tu choi (ma o D9, vd {@code REJECTED}, {@code FILE_TOO_LARGE});
 *               {@code null} khi dong y.
 */
public record FileAnswerPayload(String fileId, boolean accepted, String reason) {
}
