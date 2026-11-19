package com.datn.chatp2p.client.session;

import java.nio.file.Path;

/**
 * Trang thai hien tai cua 1 lan truyen file - {@link ChatSession} gui len giao
 * dien qua {@link ChatSessionListener#onFileTransferUpdated(FileTransferStatus)}
 * moi khi co thay doi (offer moi, tien do, xong, loi...).
 *
 * @param fileId           id lan truyen (trung {@code FileOfferPayload.fileId})
 * @param peerId           nguoi gui / nguoi nhan phia ben kia
 * @param direction        minh gui di hay nhan ve
 * @param direct           {@code true} neu gui trong tin nhan rieng (DM)
 * @param bytesTransferred so byte da gui (da duoc ACK) / da nhan
 * @param savedFile        file da luu - chi co khi nhan xong ({@link State#COMPLETED})
 * @param reason           ma ly do (D9: REJECTED, CANCELLED_BY_USER, FILE_TOO_LARGE,
 *                         INVALID_OFFER, CHECKSUM_MISMATCH, TIMEOUT, IO_ERROR) hoac
 *                         {@link #PEER_LEFT} / {@link #SEND_FAILED}; {@code null} neu khong co
 */
public record FileTransferStatus(
        String fileId,
        String peerId,
        Direction direction,
        boolean direct,
        String fileName,
        long fileSize,
        State state,
        long bytesTransferred,
        Path savedFile,
        String reason) {

    /** Ma ly do noi bo (khong gui qua mang): nguoi ben kia roi phong giua chung. */
    public static final String PEER_LEFT = "PEER_LEFT";

    /** Ma ly do noi bo: nguoi ben kia bam Huy (ho gui FILE_CANCEL CANCELLED_BY_USER). */
    public static final String CANCELLED_BY_PEER = "CANCELLED_BY_PEER";

    /** Ma ly do noi bo: khong gui duoc goi tin toi peer. */
    public static final String SEND_FAILED = "SEND_FAILED";

    public enum Direction { OUTGOING, INCOMING }

    public enum State {
        /** Minh da gui loi moi, dang cho ben kia bam Nhan / Tu choi. */
        WAITING_ANSWER,
        /** Ben kia moi minh nhan file - cho minh bam Nhan / Tu choi. */
        OFFERED,
        /** Dang truyen chunk. */
        IN_PROGRESS,
        /** Ben nhan da du chunk, dang kiem SHA-256. */
        VERIFYING,
        COMPLETED,
        REJECTED,
        CANCELLED,
        FAILED;

        public boolean isFinal() {
            return this == COMPLETED || this == REJECTED || this == CANCELLED || this == FAILED;
        }
    }

    /** Tien do 0..1. */
    public double progress() {
        if (state == State.COMPLETED || state == State.VERIFYING) {
            return 1.0;
        }
        return fileSize <= 0 ? 0.0 : Math.min(1.0, (double) bytesTransferred / fileSize);
    }

    FileTransferStatus with(State newState, long newBytes, Path newSavedFile, String newReason) {
        return new FileTransferStatus(fileId, peerId, direction, direct, fileName, fileSize,
                newState, newBytes, newSavedFile, newReason);
    }
}
