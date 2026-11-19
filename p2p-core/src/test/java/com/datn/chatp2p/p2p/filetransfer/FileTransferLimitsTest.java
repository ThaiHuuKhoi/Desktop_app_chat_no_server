package com.datn.chatp2p.p2p.filetransfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileTransferLimitsTest {

    @Test
    void totalChunksRoundsUpAndEmptyFileHasNoChunk() {
        assertEquals(0, FileTransferLimits.totalChunks(0, 4096));
        assertEquals(1, FileTransferLimits.totalChunks(1, 4096));
        assertEquals(1, FileTransferLimits.totalChunks(4096, 4096));
        assertEquals(2, FileTransferLimits.totalChunks(4097, 4096));
        assertEquals(3, FileTransferLimits.totalChunks(10_000, 4096));
    }

    @Test
    void defaultLimitsMatchTheAgreedProtocol() {
        assertEquals(100L * 1024 * 1024, FileTransferLimits.DEFAULT.maxFileSize());
        assertEquals(4096, FileTransferLimits.DEFAULT.chunkSize());
    }

    @Test
    void rejectsChunkSizeOutsideOneTo32Kb() {
        assertThrows(IllegalArgumentException.class, () -> new FileTransferLimits(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new FileTransferLimits(1000, 32 * 1024 + 1));
    }

    @Test
    void rejectsNegativeMaxFileSize() {
        assertThrows(IllegalArgumentException.class, () -> new FileTransferLimits(-1, 4096));
    }
}
