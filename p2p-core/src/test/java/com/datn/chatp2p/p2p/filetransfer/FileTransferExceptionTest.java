package com.datn.chatp2p.p2p.filetransfer;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileTransferExceptionTest {

    @Test
    void keepsReasonAndMessage() {
        FileTransferException e = new FileTransferException(
                FileTransferException.Reason.CHECKSUM_MISMATCH, "hash khong khop");

        assertEquals(FileTransferException.Reason.CHECKSUM_MISMATCH, e.reason());
        assertEquals("hash khong khop", e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    void keepsOriginalCauseWhenWrappingIoError() {
        IOException io = new IOException("Access is denied");

        FileTransferException e = new FileTransferException(
                FileTransferException.Reason.IO_ERROR, "khong ghi duoc file", io);

        assertSame(io, e.getCause());
        assertEquals(FileTransferException.Reason.IO_ERROR, e.reason());
    }

    @Test
    void reasonNameMatchesTheCancelCodeAgreedInD9() {
        // Ten enum chinh la chuoi gui trong FileCancelPayload.reason
        assertEquals("FILE_TOO_LARGE", FileTransferException.Reason.FILE_TOO_LARGE.name());
        assertEquals("INVALID_OFFER", FileTransferException.Reason.INVALID_OFFER.name());
        assertEquals("CHECKSUM_MISMATCH", FileTransferException.Reason.CHECKSUM_MISMATCH.name());
        assertEquals("IO_ERROR", FileTransferException.Reason.IO_ERROR.name());
    }

    @Test
    void isUncheckedSoReadChunkCanThrowIt() {
        assertTrue(RuntimeException.class.isAssignableFrom(FileTransferException.class));
    }
}
