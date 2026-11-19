package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.FileAckPayload;

/**
 * Bo gui chunk tin cay cho 1 file - cung hinh dang voi {@code ReliableChunkSender}
 * cua A (docs/Thong-nhat-giao-thuc-truyen-file.md D6). Hien dung tam
 * {@link TemporaryChunkSender}; khi A xong chi can doi
 * {@link FileTransferManager.ChunkSenderFactory} mac dinh.
 */
interface OutgoingChunkSender {

    /** Bat dau gui cac chunk dau tien. */
    void start();

    /** Nhan FILE_ACK tu ben nhan. */
    void onAck(FileAckPayload ack);

    /** Dung gui (huy / peer roi phong). Khong goi listener nua. */
    void cancel();

    /** Giong {@code ReliableTransferListener} (D6). Khong duoc goi khi dang giu khoa cua bo gui. */
    interface Listener {
        void onProgress(int ackedChunks, int totalChunks);

        /** Nhan duoc ACK cuoi {@code verified = true}: ben nhan da kiem SHA-256 khop. */
        void onCompleted();

        /** @param reason ma D9, vd {@code TIMEOUT}, {@code IO_ERROR} */
        void onFailed(String reason);
    }
}
