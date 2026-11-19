package com.datn.chatp2p.p2p.filetransfer;

/**
 * Su kien cua {@link ReliableChunkSender} (D6). Duoc goi NGOAI khoa cua sender, tu thread
 * nhan ACK hoac thread hen gio - KHONG phai JavaFX thread (UI tu {@code Platform.runLater}).
 * {@link #onCompleted} va {@link #onFailed} loai tru nhau va chi goi toi da 1 lan.
 */
public interface ReliableTransferListener {

    /** So chunk da duoc ben nhan xac nhan tren tong so chunk. */
    void onProgress(int ackedChunks, int totalChunks);

    /** Ben nhan bao {@code FILE_ACK verified = true} (du chunk + SHA-256 khop). */
    void onCompleted();

    /**
     * Lan truyen that bai. {@code reason} la 1 gia tri trong
     * {@link com.datn.chatp2p.common.protocol.FileTransferReason}. Lop goi (B) chiu trach
     * nhiem gui {@code FILE_CANCEL} cho ben kia neu loi xuat phat tu phia minh
     * (TIMEOUT, IO_ERROR) - khong can gui lai neu loi do ben kia huy/tu choi.
     */
    void onFailed(String reason);
}
