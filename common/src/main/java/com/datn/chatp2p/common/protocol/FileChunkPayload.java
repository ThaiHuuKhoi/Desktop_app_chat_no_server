package com.datn.chatp2p.common.protocol;

/**
 * Payload cua {@link EnvelopeType#FILE_CHUNK} - 1 manh cua file
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D1, D3). Khong ma hoa them: ca
 * Envelope chua chunk da duoc ma hoa AES-GCM bang khoa phien cua cap peer.
 *
 * @param index thu tu chunk, tu 0 toi {@code totalChunks - 1}.
 * @param data  noi dung chunk.
 */
public record FileChunkPayload(String fileId, int index, byte[] data) {
}
