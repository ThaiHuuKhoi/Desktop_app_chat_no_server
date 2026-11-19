package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;

/**
 * Nguon chunk cho ben gui - diem noi giua phan cua B ({@link FileSender}) va
 * {@code ReliableChunkSender} cua A (docs/Thong-nhat-giao-thuc-truyen-file.md D6).
 */
public interface ChunkSource {

    /** Loi moi FILE_OFFER mo ta file se gui. */
    FileOfferPayload offer();

    /**
     * Doc chunk thu {@code index} (0 .. totalChunks - 1). Goi lai bao nhieu lan
     * cung duoc - dung khi can gui lai chunk bi mat.
     *
     * @throws IndexOutOfBoundsException neu {@code index} ngoai khoang
     * @throws FileTransferException     ({@code IO_ERROR}) neu khong doc duoc file
     */
    FileChunkPayload readChunk(int index);
}
