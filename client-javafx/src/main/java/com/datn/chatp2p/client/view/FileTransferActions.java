package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.session.FileTransferStatus;

/** Cac nut tren the file trong khung chat - {@link RoomController} xu ly. */
interface FileTransferActions {

    void accept(FileTransferStatus status);

    void reject(FileTransferStatus status);

    void cancel(FileTransferStatus status);

    /** Mo thu muc chua file da nhan (chon san file do neu duoc). */
    void showInFolder(FileTransferStatus status);

    /** Nguoi dung bam vao anh xem truoc -&gt; mo bang trinh xem anh (chi file anh, khong bao gio tu mo). */
    default void open(FileTransferStatus status) {
    }
}
