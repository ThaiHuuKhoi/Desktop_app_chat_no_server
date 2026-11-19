package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@link EnvelopeType#FILE_CANCEL} - huy hoac bao loi 1 lan truyen
 * file, gui theo ca 2 chieu (docs/Thong-nhat-giao-thuc-truyen-file.md D1, D9).
 *
 * @param reason 1 trong cac ma: {@code REJECTED}, {@code CANCELLED_BY_USER},
 *               {@code FILE_TOO_LARGE}, {@code INVALID_OFFER},
 *               {@code CHECKSUM_MISMATCH}, {@code TIMEOUT}, {@code IO_ERROR}.
 */
public record FileCancelPayload(String fileId, String reason) {
}
