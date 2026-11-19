package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.io.IOException;

/**
 * Nguon du lieu phia gui (B cai dat - {@code FileSender}). Diem noi giua A va B (D6).
 *
 * <p>{@link ReliableChunkSender} goi {@link #readChunk} tu NHIEU thread (thread nhan ACK
 * va thread hen gio gui lai) nhung KHONG BAO GIO dong thoi - moi loi goi deu nam trong
 * khoa cua sender. Cai dat van nen chiu duoc doc lai cung 1 chunk nhieu lan (gui lai).
 */
public interface ChunkSource {

    /** Thong tin file - phai on dinh (cung 1 doi tuong/gia tri) trong suot lan truyen. */
    FileOfferPayload offer();

    /**
     * Doc chunk thu {@code index} (0 &le; index &lt; totalChunks). Moi chunk dai dung
     * {@code chunkSize} tru chunk cuoi (D3).
     *
     * @throws IOException loi doc dia - sender se dung va bao {@code IO_ERROR}.
     */
    FileChunkPayload readChunk(int index) throws IOException;
}
