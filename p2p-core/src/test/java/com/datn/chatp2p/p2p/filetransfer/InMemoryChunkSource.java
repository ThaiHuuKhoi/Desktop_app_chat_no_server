package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** {@link ChunkSource} gia cho test cua A - du lieu ngau nhien trong bo nho (D6). */
final class InMemoryChunkSource implements ChunkSource {

    private final byte[] data;
    private final FileOfferPayload offer;
    final AtomicInteger reads = new AtomicInteger();

    InMemoryChunkSource(byte[] data, int chunkSize) {
        this(data, chunkSize, sha256Hex(data));
    }

    /** Cho phep khai SHA-256 sai de test CHECKSUM_MISMATCH. */
    InMemoryChunkSource(byte[] data, int chunkSize, String claimedSha256) {
        this.data = data;
        this.offer = new FileOfferPayload(UUID.randomUUID().toString(), "test.bin", data.length,
                "application/octet-stream", chunkSize,
                FileTransferLimits.expectedChunkCount(data.length, chunkSize), claimedSha256);
    }

    static InMemoryChunkSource random(int size, int chunkSize, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return new InMemoryChunkSource(bytes, chunkSize);
    }

    byte[] data() {
        return data;
    }

    @Override
    public FileOfferPayload offer() {
        return offer;
    }

    @Override
    public FileChunkPayload readChunk(int index) {
        reads.incrementAndGet();
        int from = index * offer.chunkSize();
        int to = Math.min(from + offer.chunkSize(), data.length);
        return new FileChunkPayload(offer.fileId(), index, Arrays.copyOfRange(data, from, to));
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
