package com.datn.chatp2p.p2p.filetransfer;

/**
 * Tham so tin cay cua {@link ReliableChunkSender} (D5) - gia tri ban dau, se do roi chinh.
 *
 * @param windowSize          so chunk toi da dang cho xac nhan cung luc.
 * @param initialRetryMillis  cho bao lau truoc lan gui lai dau tien; gap doi moi lan.
 * @param maxRetryMillis      tran cua thoi gian cho giua 2 lan gui lai.
 * @param maxRetries          so lan gui lai toi da cho 1 chunk (va cho buoc cho xac nhan
 *                            SHA-256 cuoi cung) - qua thi {@code TIMEOUT}.
 * @param offerRetryMillis    chu ky gui lai {@code FILE_OFFER} khi chua co {@code FILE_ANSWER}.
 * @param answerTimeoutMillis cho nguoi nhan tra loi offer bao lau - qua thi {@code TIMEOUT}.
 */
public record ReliableTransferConfig(int windowSize, long initialRetryMillis, long maxRetryMillis,
                                     int maxRetries, long offerRetryMillis, long answerTimeoutMillis) {

    public static final ReliableTransferConfig DEFAULT =
            new ReliableTransferConfig(32, 500, 4_000, 10, 1_000, 60_000);

    public ReliableTransferConfig {
        if (windowSize <= 0 || initialRetryMillis <= 0 || maxRetryMillis < initialRetryMillis
                || maxRetries < 0 || offerRetryMillis <= 0 || answerTimeoutMillis <= 0) {
            throw new IllegalArgumentException("ReliableTransferConfig khong hop le");
        }
    }

    /** Thoi gian cho truoc lan gui lai thu {@code attempt + 1}: initial * 2^attempt, toi da maxRetryMillis. */
    long backoffMillis(int attempt) {
        long delay = initialRetryMillis << Math.min(attempt, 20);
        return Math.min(delay, maxRetryMillis);
    }
}
