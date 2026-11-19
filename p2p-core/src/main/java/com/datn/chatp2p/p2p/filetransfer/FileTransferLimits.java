package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.util.regex.Pattern;

/**
 * Gioi han va kiem tra hop le chung cho ca 2 phia (D3, D5, D8).
 *
 * <p>Kich thuoc gui qua {@code P2pDataChannel}: payload bi Base64 2 lan (FileChunkPayload.data
 * trong JSON, roi Envelope.payload trong JSON) ~x1,78 + AES-GCM 28 byte + 4 byte length-prefix.
 * Chunk 16 KB -> ~29 KB/goi, con xa gioi han 65 507 byte cua 1 datagram ke ca khi qua TURN;
 * 32 KB -> ~58,5 KB, qua sat gioi han nen tran duoc ha xuong 16 KB.
 */
public final class FileTransferLimits {

    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    public static final int DEFAULT_CHUNK_SIZE = 4 * 1024;
    public static final int MAX_CHUNK_SIZE = 16 * 1024;

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private FileTransferLimits() {
    }

    /** {@code ceil(fileSize / chunkSize)}; file rong = 0 chunk. */
    public static int expectedChunkCount(long fileSize, int chunkSize) {
        return (int) ((fileSize + chunkSize - 1) / chunkSize);
    }

    /**
     * Kiem tra 1 offer dung quy uoc D3/D5/D8 (khong kiem tra ten file - ben nhan tu lam sach).
     *
     * @return null neu hop le, nguoc lai la mo ta loi (de log; ma gui di la INVALID_OFFER /
     *         FILE_TOO_LARGE tuy truong hop - xem {@link #isTooLarge}).
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

    public static boolean isTooLarge(FileOfferPayload offer) {
        return offer.fileSize() > MAX_FILE_SIZE;
    }

    /** Do dai dung cua chunk {@code index} theo D3. */
    public static int expectedChunkLength(FileOfferPayload offer, int index) {
        if (index < offer.totalChunks() - 1) {
            return offer.chunkSize();
        }
        long remainder = offer.fileSize() - (long) index * offer.chunkSize();
        return (int) remainder;
    }
}
