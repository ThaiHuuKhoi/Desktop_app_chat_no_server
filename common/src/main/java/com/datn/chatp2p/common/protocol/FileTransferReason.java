package com.datn.chatp2p.common.protocol;

/** Ma ly do huy / loi khi truyen file (D9) - dung cho {@code reason} cua FILE_ANSWER / FILE_CANCEL. */
public final class FileTransferReason {

    /** Nguoi nhan bam "Tu choi". */
    public static final String REJECTED = "REJECTED";
    /** 1 ben bam "Huy" giua chung. */
    public static final String CANCELLED_BY_USER = "CANCELLED_BY_USER";
    /** Vuot gioi han dung luong (100 MB). */
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    /** FILE_OFFER sai (so chunk, kich thuoc chunk, SHA-256 sai dinh dang...). */
    public static final String INVALID_OFFER = "INVALID_OFFER";
    /** SHA-256 file nhan duoc khong khop. */
    public static final String CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    /** Het luot gui lai, hoac qua lau khong tra loi offer / khong xac nhan. */
    public static final String TIMEOUT = "TIMEOUT";
    /** Loi doc / ghi dia, hoac kenh gui da dong. */
    public static final String IO_ERROR = "IO_ERROR";
    /** Ben nhan dang truyen 1 file khac voi peer nay (MVP: 1 file / peer / luc). */
    public static final String BUSY = "BUSY";

    private FileTransferReason() {
    }
}
