package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.util.regex.Pattern;

/**
 * Gioi han va kiem tra hop le chung cho ca 2 phia (D3, D5, D8) - gop ban cua
 * B (record cau hinh: {@link #DEFAULT}, {@link #totalChunks}) va ban cua A
 * (kiem tra offer: {@link #validate}, {@link #isTooLarge}, {@link #expectedChunkLength}).
 *
 * <p>Kich thuoc gui qua {@code P2pDataChannel}: payload bi Base64 2 lan (FileChunkPayload.data
 * trong JSON, roi Envelope.payload trong JSON) ~x1,78 + AES-GCM 28 byte + 4 byte length-prefix.
 * Chunk 16 KB -> ~29 KB/goi, con xa gioi han 65 507 byte cua 1 datagram ke ca khi qua TURN;
 * 32 KB -> ~58,5 KB, qua sat gioi han nen tran duoc ha xuong 16 KB (A).
 *
 * <p>LUU Y: tren mang that, ice4j ({@code MergingDatagramSocket}) hien cat moi goi o
 * 1500 byte - client dang gui chunk 512 byte cho toi khi {@code P2pDataChannel} chia
 * nho goi (xem {@code FileTransferManager.NETWORK_CHUNK_SIZE}).
 *
 * @param maxFileSize dung luong file toi da (byte)
 * @param chunkSize   kich thuoc chunk ben gui dung khi tao offer
 */
public record FileTransferLimits(long maxFileSize, int chunkSize) {

    public static final long DEFAULT_MAX_FILE_SIZE = 100L * 1024 * 1024;
    /** Ten cua A cho {@link #DEFAULT_MAX_FILE_SIZE}. */
    public static final long MAX_FILE_SIZE = DEFAULT_MAX_FILE_SIZE;
    public static final int DEFAULT_CHUNK_SIZE = 4 * 1024;
    public static final int MAX_CHUNK_SIZE = 16 * 1024;

    public static final FileTransferLimits DEFAULT =
            new FileTransferLimits(DEFAULT_MAX_FILE_SIZE, DEFAULT_CHUNK_SIZE);

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public FileTransferLimits {
        if (maxFileSize < 0) {
            throw new IllegalArgumentException("maxFileSize khong duoc am, nhan duoc " + maxFileSize);
        }
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize phai trong khoang 1.." + MAX_CHUNK_SIZE + ", nhan duoc " + chunkSize);
        }
    }

    /**
     * So chunk cua 1 file = {@code ceil(fileSize / chunkSize)}; file rong = 0 chunk.
     * Kiem tra tham so va khong tran so.
     */
    public static int totalChunks(long fileSize, int chunkSize) {
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize khong duoc am, nhan duoc " + fileSize);
        }
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize phai trong khoang 1.." + MAX_CHUNK_SIZE + ", nhan duoc " + chunkSize);
        }
        long total = fileSize / chunkSize + (fileSize % chunkSize == 0 ? 0 : 1);
        return Math.toIntExact(total);
    }

    /** {@code ceil(fileSize / chunkSize)} (ten cua A, khong kiem tra tham so); file rong = 0 chunk. */
    public static int expectedChunkCount(long fileSize, int chunkSize) {
        return (int) ((fileSize + chunkSize - 1) / chunkSize);
    }

    /**
     * Kiem tra offer theo D3/D5 (A).
     *
     * @return {@code null} neu hop le, nguoc lai la mo ta loi (-> {@code INVALID_OFFER}).
     */
    public static String validate(FileOfferPayload offer) {
        if (offer == null) {
            return "offer null";
        }
        if (offer.fileId() == null || offer.fileId().isBlank()) {
            return "thieu fileId";
        }
        if (offer.fileSize() < 0) {
            return "fileSize am";
        }
        if (offer.chunkSize() <= 0 || offer.chunkSize() > MAX_CHUNK_SIZE) {
            return "chunkSize ngoai khoang (0, " + MAX_CHUNK_SIZE + "]";
        }
        if (offer.totalChunks() != expectedChunkCount(offer.fileSize(), offer.chunkSize())) {
            return "totalChunks khong khop ceil(fileSize / chunkSize)";
        }
        if (offer.sha256() == null || !SHA256_HEX.matcher(offer.sha256()).matches()) {
            return "sha256 phai la 64 ky tu hex chu thuong";
        }
        return null;
    }

    /** {@code true} neu file vuot gioi han D8 (-> {@code FILE_TOO_LARGE}). */
    public static boolean isTooLarge(FileOfferPayload offer) {
        return offer.fileSize() > MAX_FILE_SIZE;
    }

    /** Do dai dung cua chunk {@code index}: {@code chunkSize}, rieng chunk cuoi la phan con lai. */
    public static int expectedChunkLength(FileOfferPayload offer, int index) {
        if (index < offer.totalChunks() - 1) {
            return offer.chunkSize();
        }
        long remainder = offer.fileSize() - (long) index * offer.chunkSize();
        return (int) remainder;
    }
}
