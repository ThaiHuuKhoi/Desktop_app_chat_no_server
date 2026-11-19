package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;
import com.datn.chatp2p.p2p.filetransfer.FileReceiver.ChunkResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileReceiverTest {

    @TempDir
    Path temp;

    private byte[] content;

    private Path source(String name, int size) throws Exception {
        content = new byte[size];
        new Random(size).nextBytes(content);
        Path sourceDir = Files.createDirectories(temp.resolve("source"));
        return Files.write(sourceDir.resolve(name), content);
    }

    private Path downloads() {
        return temp.resolve("downloads");
    }

    private List<Path> filesIn(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        }
    }

    /** Ban sao cua offer voi ten / totalChunks / sha256 khac - gia lap peer gui offer sai. */
    private static FileOfferPayload withName(FileOfferPayload o, String name) {
        return new FileOfferPayload(o.fileId(), name, o.fileSize(), o.mimeType(), o.chunkSize(), o.totalChunks(), o.sha256());
    }

    private static FileOfferPayload withTotalChunks(FileOfferPayload o, int totalChunks) {
        return new FileOfferPayload(o.fileId(), o.fileName(), o.fileSize(), o.mimeType(), o.chunkSize(), totalChunks, o.sha256());
    }

    private static FileOfferPayload withSha256(FileOfferPayload o, String sha256) {
        return new FileOfferPayload(o.fileId(), o.fileName(), o.fileSize(), o.mimeType(), o.chunkSize(), o.totalChunks(), sha256);
    }

    // ---------------------------------------------------------------- nhan thanh cong

    @Test
    void chunksInOrderRebuildTheFile() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            for (int i = 0; i < sender.offer().totalChunks(); i++) {
                assertEquals(ChunkResult.ACCEPTED, receiver.onChunk(sender.readChunk(i)));
            }
            assertTrue(receiver.isComplete());

            Path result = receiver.complete();
            assertEquals(downloads().resolve("goc.bin"), result);
            assertArrayEquals(content, Files.readAllBytes(result));
            assertEquals(List.of(result), filesIn(downloads())); // khong con file .part
        }
    }

    @Test
    void chunksArrivingInReverseOrderStillRebuildTheFile() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            for (int i = sender.offer().totalChunks() - 1; i >= 0; i--) {
                assertEquals(ChunkResult.ACCEPTED, receiver.onChunk(sender.readChunk(i)));
            }

            assertArrayEquals(content, Files.readAllBytes(receiver.complete()));
        }
    }

    @Test
    void duplicateChunksAreIgnored() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            for (int i = 0; i < sender.offer().totalChunks(); i++) {
                assertEquals(ChunkResult.ACCEPTED, receiver.onChunk(sender.readChunk(i)));
                assertEquals(ChunkResult.DUPLICATE, receiver.onChunk(sender.readChunk(i)));
            }
            assertEquals(3, receiver.receivedChunks());

            assertArrayEquals(content, Files.readAllBytes(receiver.complete()));
        }
    }

    @Test
    void emptyFileIsCompleteImmediately() throws Exception {
        try (FileSender sender = FileSender.open(source("rong.txt", 0));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            assertTrue(receiver.isComplete());
            Path result = receiver.complete();
            assertEquals(0, Files.size(result));
        }
    }

    @Test
    void oneMegabyteShuffledWithDuplicatesArrivesByteForByte() throws Exception {
        try (FileSender sender = FileSender.open(source("lon.bin", 1024 * 1024));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            int total = sender.offer().totalChunks();
            Random random = new Random(7);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                order.add(i);
                if (random.nextInt(5) == 0) {
                    order.add(i); // ~20% chunk bi gui lai
                }
            }
            Collections.shuffle(order, random);

            for (int index : order) {
                ChunkResult result = receiver.onChunk(sender.readChunk(index));
                assertTrue(result == ChunkResult.ACCEPTED || result == ChunkResult.DUPLICATE);
            }

            assertEquals(total, receiver.receivedChunks());
            assertArrayEquals(content, Files.readAllBytes(receiver.complete()));
        }
    }

    // ---------------------------------------------------------------- chunk sai

    @Test
    void invalidChunksAreRejected() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            String fileId = sender.offer().fileId();
            byte[] full = new byte[4096];
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(null));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload("file-khac", 0, full)));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload(fileId, -1, full)));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload(fileId, 3, full)));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload(fileId, 0, null)));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload(fileId, 0, new byte[100])));
            // chunk cuoi phai dung 1808 byte, khong phai 4096
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(new FileChunkPayload(fileId, 2, full)));

            assertEquals(0, receiver.receivedChunks());
        }
    }

    @Test
    void rejectedChunkIsNotMarkedAsReceived() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            receiver.onChunk(new FileChunkPayload(sender.offer().fileId(), 0, new byte[100]));

            // Chunk 0 dung den sau van phai duoc nhan, khong bi coi la DUPLICATE
            assertEquals(ChunkResult.ACCEPTED, receiver.onChunk(sender.readChunk(0)));
        }
    }

    // ---------------------------------------------------------------- offer sai

    @Test
    void invalidOffersAreRejected() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000))) {
            FileOfferPayload good = sender.offer();

            assertInvalidOffer(null);
            assertInvalidOffer(new FileOfferPayload(null, "a", 10, null, 4096, 1, good.sha256()));
            assertInvalidOffer(new FileOfferPayload(" ", "a", 10, null, 4096, 1, good.sha256()));
            assertInvalidOffer(new FileOfferPayload("id", "a", -1, null, 4096, 0, good.sha256()));
            assertInvalidOffer(new FileOfferPayload("id", "a", 10, null, 0, 1, good.sha256()));
            assertInvalidOffer(new FileOfferPayload("id", "a", 10, null, 32 * 1024 + 1, 1, good.sha256()));
            assertInvalidOffer(withTotalChunks(good, 4));
            assertInvalidOffer(withTotalChunks(good, 2));
            assertInvalidOffer(withSha256(good, "xyz"));
            assertInvalidOffer(withSha256(good, good.sha256().toUpperCase()));
            assertInvalidOffer(withSha256(good, null));
        }
        assertFalse(Files.exists(downloads()), "Offer sai khong duoc tao thu muc / file tam");
    }

    private void assertInvalidOffer(FileOfferPayload offer) {
        FileTransferException e = assertThrows(FileTransferException.class,
                () -> FileReceiver.accept(offer, downloads()));
        assertEquals(FileTransferException.Reason.INVALID_OFFER, e.reason());
    }

    @Test
    void offerLargerThanLimitIsRejected() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000))) {
            FileTransferException e = assertThrows(FileTransferException.class,
                    () -> FileReceiver.accept(sender.offer(), downloads(), new FileTransferLimits(9_999, 4096)));
            assertEquals(FileTransferException.Reason.FILE_TOO_LARGE, e.reason());
        }
    }

    // ---------------------------------------------------------------- SHA-256

    @Test
    void wrongChecksumDeletesThePartFile() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000))) {
            FileOfferPayload lying = withSha256(sender.offer(), "0".repeat(64)); // dung dinh dang, sai gia tri

            try (FileReceiver receiver = FileReceiver.accept(lying, downloads())) {
                for (int i = 0; i < lying.totalChunks(); i++) {
                    receiver.onChunk(sender.readChunk(i));
                }

                FileTransferException e = assertThrows(FileTransferException.class, receiver::complete);
                assertEquals(FileTransferException.Reason.CHECKSUM_MISMATCH, e.reason());
                assertEquals(List.of(), filesIn(downloads()));
            }
        }
    }

    @Test
    void completeBeforeAllChunksFails() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            receiver.onChunk(sender.readChunk(0));

            assertThrows(IllegalStateException.class, receiver::complete);
        }
    }

    // ---------------------------------------------------------------- ten file (D10)

    @Test
    void pathTraversalNameStaysInsideTheChosenDirectory() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000))) {
            FileOfferPayload evil = withName(sender.offer(), "../../evil.exe");

            try (FileReceiver receiver = FileReceiver.accept(evil, downloads())) {
                for (int i = 0; i < evil.totalChunks(); i++) {
                    receiver.onChunk(sender.readChunk(i));
                }
                Path result = receiver.complete();

                assertEquals(downloads().resolve("evil.exe"), result);
                assertFalse(Files.exists(temp.getParent().resolve("evil.exe")));
            }
        }
    }

    @Test
    void existingFileIsNeverOverwritten() throws Exception {
        Files.createDirectories(downloads());
        Path existing = Files.writeString(downloads().resolve("goc.bin"), "file cu");

        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            for (int i = 0; i < sender.offer().totalChunks(); i++) {
                receiver.onChunk(sender.readChunk(i));
            }
            Path result = receiver.complete();

            assertEquals(downloads().resolve("goc (1).bin"), result);
            assertArrayEquals(content, Files.readAllBytes(result));
            assertEquals("file cu", Files.readString(existing));
        }
    }

    // ---------------------------------------------------------------- huy

    @Test
    void cancelDeletesThePartFileAndRejectsLaterChunks() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            receiver.onChunk(sender.readChunk(0));
            assertEquals(1, filesIn(downloads()).size()); // file .part dang ghi

            receiver.cancel();

            assertEquals(List.of(), filesIn(downloads()));
            assertEquals(ChunkResult.REJECTED, receiver.onChunk(sender.readChunk(1)));
            assertThrows(IllegalStateException.class, receiver::complete);
        }
    }

    @Test
    void closingAfterCompleteKeepsTheFile() throws Exception {
        Path result;
        try (FileSender sender = FileSender.open(source("goc.bin", 10_000));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {
            for (int i = 0; i < sender.offer().totalChunks(); i++) {
                receiver.onChunk(sender.readChunk(i));
            }
            result = receiver.complete();
        }
        assertArrayEquals(content, Files.readAllBytes(result));
    }

    // ---------------------------------------------------------------- thong tin cho FILE_ACK (D4)

    @Test
    void ackInformationReportsContiguousAndSelectiveChunks() throws Exception {
        try (FileSender sender = FileSender.open(source("goc.bin", 8 * 4096));
             FileReceiver receiver = FileReceiver.accept(sender.offer(), downloads())) {

            assertEquals(0, receiver.contiguousChunks());
            assertEquals(List.of(), receiver.receivedAbove(64));

            for (int i : new int[]{0, 1, 2, 5, 7}) {
                receiver.onChunk(sender.readChunk(i));
            }

            assertEquals(3, receiver.contiguousChunks());
            assertEquals(List.of(5, 7), receiver.receivedAbove(64));
            assertEquals(List.of(5), receiver.receivedAbove(1));

            for (int i : new int[]{3, 4, 6}) {
                receiver.onChunk(sender.readChunk(i));
            }
            assertEquals(8, receiver.contiguousChunks());
            assertEquals(List.of(), receiver.receivedAbove(64));
        }
    }
}
