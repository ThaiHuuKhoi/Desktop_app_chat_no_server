package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@link EnvelopeType#FILE_OFFER} - loi moi gui 1 file, gui truoc
 * moi chunk de nguoi nhan dong y / tu choi (docs/Thong-nhat-giao-thuc-truyen-file.md D1, D3).
 *
 * @param fileId      UUID ngau nhien cua lan gui nay, gan vao moi bon tin FILE_* sau do.
 * @param fileName    ten file goc - ben nhan PHAI lam sach truoc khi ghi xuong dia (D10).
 * @param fileSize    dung luong (byte).
 * @param mimeType    kieu noi dung doan tu ten file, co the {@code null}.
 * @param chunkSize   kich thuoc moi chunk (byte); moi chunk dung bang gia tri nay tru chunk cuoi.
 * @param totalChunks {@code ceil(fileSize / chunkSize)}; file rong thi 0.
 * @param sha256      SHA-256 cua ca file, hex chu thuong 64 ky tu.
 */
public record FileOfferPayload(
        String fileId,
        String fileName,
        long fileSize,
        String mimeType,
        int chunkSize,
        int totalChunks,
        String sha256) {
}
