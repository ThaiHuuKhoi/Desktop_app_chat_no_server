package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSenderTest {

    @TempDir
    Path dir;

    private Path randomFile(String name, int size) throws Exception {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return Files.write(dir.resolve(name), data);
    }

    @Test
    void emptyFileHasNoChunk() throws Exception {
        Path file = Files.createFile(dir.resolve("rong.txt"));

        try (FileSender sender = FileSender.open(file)) {
            assertEquals(0, sender.offer().fileSize());
            assertEquals(0, sender.offer().totalChunks());
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    sender.offer().sha256());
            assertThrows(IndexOutOfBoundsException.class, () -> sender.readChunk(0));
        }
    }

    @Test
    void offerDescribesTheFile() throws Exception {
        Path file = randomFile("bao cao.pdf", 10_000);

        try (FileSender sender = FileSender.open(file)) {
            FileOfferPayload offer = sender.offer();
            assertEquals("bao cao.pdf", offer.fileName());
            assertEquals(10_000, offer.fileSize());
            assertEquals(4096, offer.chunkSize());
            assertEquals(3, offer.totalChunks());
            assertEquals(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))), offer.sha256());
        }
    }

    @Test
    void eachOpenGetsANewFileId() throws Exception {
        Path file = randomFile("a.bin", 10);

        try (FileSender first = FileSender.open(file); FileSender second = FileSender.open(file)) {
            assertNotEquals(first.offer().fileId(), second.offer().fileId());
        }
    }

    @Test
    void exactMultipleOfChunkSizeHasFullChunks() throws Exception {
        Path file = randomFile("a.bin", 2 * 4096);

        try (FileSender sender = FileSender.open(file)) {
            assertEquals(2, sender.offer().totalChunks());
            assertEquals(4096, sender.readChunk(0).data().length);
            assertEquals(4096, sender.readChunk(1).data().length);
        }
    }

    @Test
    void lastChunkIsShorterAndChunksRebuildTheFile() throws Exception {
        Path file = randomFile("a.bin", 10_000);

        try (FileSender sender = FileSender.open(file)) {
            ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
            int[] expectedLengths = {4096, 4096, 1808};
            for (int i = 0; i < sender.offer().totalChunks(); i++) {
                FileChunkPayload chunk = sender.readChunk(i);
                assertEquals(sender.offer().fileId(), chunk.fileId());
                assertEquals(i, chunk.index());
                assertEquals(expectedLengths[i], chunk.data().length);
                rebuilt.write(chunk.data());
            }
            assertArrayEquals(Files.readAllBytes(file), rebuilt.toByteArray());
        }
    }

    @Test
    void sameChunkCanBeReadAgainForRetransmission() throws Exception {
        Path file = randomFile("a.bin", 10_000);

        try (FileSender sender = FileSender.open(file)) {
            byte[] first = sender.readChunk(1).data();
            sender.readChunk(2);
            byte[] again = sender.readChunk(1).data();
            assertArrayEquals(first, again);
        }
    }

    @Test
    void rejectsIndexOutsideRange() throws Exception {
        Path file = randomFile("a.bin", 10_000);

        try (FileSender sender = FileSender.open(file)) {
            assertThrows(IndexOutOfBoundsException.class, () -> sender.readChunk(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> sender.readChunk(3));
        }
    }

    @Test
    void customChunkSizeIsUsed() throws Exception {
        Path file = randomFile("a.bin", 1000);

        try (FileSender sender = FileSender.open(file, new FileTransferLimits(10_000, 256))) {
            assertEquals(256, sender.offer().chunkSize());
            assertEquals(4, sender.offer().totalChunks());
            assertEquals(1000 - 3 * 256, sender.readChunk(3).data().length);
        }
    }

    @Test
    void fileLargerThanLimitIsRejected() throws Exception {
        Path file = randomFile("lon.bin", 1001);

        FileTransferException e = assertThrows(FileTransferException.class,
                () -> FileSender.open(file, new FileTransferLimits(1000, 256)));
        assertEquals(FileTransferException.Reason.FILE_TOO_LARGE, e.reason());
    }

    @Test
    void directoryIsNotAFile() {
        assertThrows(IllegalArgumentException.class, () -> FileSender.open(dir));
    }

    @Test
    void readingAfterCloseFailsWithIoError() throws Exception {
        Path file = randomFile("a.bin", 10);
        FileSender sender = FileSender.open(file);
        sender.close();

        FileTransferException e = assertThrows(FileTransferException.class, () -> sender.readChunk(0));
        assertEquals(FileTransferException.Reason.IO_ERROR, e.reason());
    }
}
