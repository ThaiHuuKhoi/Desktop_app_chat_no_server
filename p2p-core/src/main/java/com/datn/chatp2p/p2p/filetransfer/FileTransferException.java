
package com.datn.chatp2p.p2p.filetransfer;

public class FileTransferException extends RuntimeException {

    // Các nguyên nhân gây lỗi trong quá trình truyền tệp
    public enum Reason {
        FILE_TOO_LARGE,
        INVALID_OFFER,
        CHECKSUM_MISMATCH,
        IO_ERROR
    }

    // Lưu nguyên nhân gây lỗi
    private final Reason reason;

    /**
     * Constructor 1:
     * Khởi tạo ngoại lệ với nguyên nhân và thông báo lỗi.
     */
    public FileTransferException(Reason reason, String message) {
        super(message);

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Reason must not be null"
            );
        }

        this.reason = reason;
    }

    /**
     * Constructor 2:
     * Khởi tạo ngoại lệ với nguyên nhân, thông báo
     * và ngoại lệ gốc.
     */
    public FileTransferException(
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Reason must not be null"
            );
        }

        this.reason = reason;
    }

    /**
     * Lấy nguyên nhân gây lỗi.
     */
    public Reason reason() {
        return reason;
    }
}
