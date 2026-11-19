package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Ben nhan: ghi cac chunk (den theo thu tu bat ky, co the trung) vao file tam
 * {@code .part}, kiem SHA-256 roi doi ten thanh file that
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D3, D4, D10).
 *
 * <p>Moi ham public deu {@code synchronized}: {@link #onChunk} duoc goi tu luong
 * nhan mang, con {@link #cancel()} co the duoc goi tu luong giao dien.</p>
 */
public final class FileReceiver implements AutoCloseable {

    /** Ket qua xu ly 1 chunk. */
    public enum ChunkResult {
        /** Chunk moi, da ghi xuong dia. */
        ACCEPTED,
        /** Chunk da nhan truoc do (ben gui gui lai) - bo qua, khong ghi lai. */
        DUPLICATE,
        /** Chunk sai (fileId, index, do dai) hoac da ket thuc - bo qua. */
        REJECTED
    }

    /** So lan thu ten khac neu ngay luc doi ten co file trung ten vua xuat hien. */
    private static final int MAX_MOVE_ATTEMPTS = 10;

    private final FileOfferPayload offer;
    private final Path directory;
    private final String safeName;
    private final Path partFile;
    private final FileChannel channel;
    private final BitSet received;
    private int receivedCount;
    private boolean finished;

    private FileReceiver(FileOfferPayload offer, Path directory, String safeName,
                         Path partFile, FileChannel channel) {
        this.offer = offer;
        this.directory = directory;
        this.safeName = safeName;
        this.partFile = partFile;
        this.channel = channel;
        this.received = new BitSet(offer.totalChunks());
    }

    public static FileReceiver accept(FileOfferPayload offer, Path directory) {
        return accept(offer, directory, FileTransferLimits.DEFAULT);
    }

    /**
     * Chap nhan FILE_OFFER va tao file tam {@code .part} trong {@code directory}.
     *
     * @throws FileTransferException {@code INVALID_OFFER}, {@code FILE_TOO_LARGE}
     *                               hoac {@code IO_ERROR}
     */
    public static FileReceiver accept(FileOfferPayload offer, Path directory, FileTransferLimits limits) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(limits, "limits");
        validateOffer(offer, limits);

        String safeName = FileNames.sanitize(offer.fileName());
        Path partFile = null;
        try {
            Files.createDirectories(directory);
            partFile = Files.createTempFile(directory, safeName + ".", ".part");
            FileChannel channel = FileChannel.open(partFile, StandardOpenOption.WRITE);
            return new FileReceiver(offer, directory, safeName, partFile, channel);
        } catch (IOException e) {
            deleteQuietly(partFile);
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong tao duoc file tam trong " + directory, e);
        }
    }

    /** Kiem tra FILE_OFFER tu peer - du lieu khong tin cay (D3, D10). */
    static void validateOffer(FileOfferPayload offer, FileTransferLimits limits) {
        if (offer == null) {
            throw invalidOffer("offer null");
        }
        if (offer.fileId() == null || offer.fileId().isBlank()) {
            throw invalidOffer("thieu fileId");
        }
        if (offer.fileSize() < 0) {
            throw invalidOffer("fileSize am: " + offer.fileSize());
        }
        if (offer.chunkSize() < 1 || offer.chunkSize() > FileTransferLimits.MAX_CHUNK_SIZE) {
            throw invalidOffer("chunkSize ngoai khoang 1.." + FileTransferLimits.MAX_CHUNK_SIZE
                    + ": " + offer.chunkSize());
        }
        if (offer.fileSize() > limits.maxFileSize()) {
            throw new FileTransferException(FileTransferException.Reason.FILE_TOO_LARGE,
                    "File " + offer.fileSize() + " byte vuot gioi han " + limits.maxFileSize() + " byte");
        }
        int expectedChunks;
        try {
            expectedChunks = FileTransferLimits.totalChunks(offer.fileSize(), offer.chunkSize());
        } catch (ArithmeticException e) {
            throw invalidOffer("qua nhieu chunk");
        }
        if (offer.totalChunks() != expectedChunks) {
            throw invalidOffer("totalChunks = " + offer.totalChunks() + ", dung ra phai la " + expectedChunks);
        }
        if (!Sha256.isValidHex(offer.sha256())) {
            throw invalidOffer("sha256 khong phai 64 ky tu hex chu thuong");
        }
    }

    /**
     * Xu ly 1 chunk. Thu tu kiem tra quan trong: kiem tra hop le (1-4) dat TRUOC
     * kiem tra trung (5), de chunk sai khong bao gio duoc danh dau "da co".
     *
     * @throws FileTransferException ({@code IO_ERROR}) neu ghi dia that bai -
     *                               lan truyen bi huy va file tam bi xoa
     */
    public synchronized ChunkResult onChunk(FileChunkPayload chunk) {
        if (finished) {                                                          // 1
            return ChunkResult.REJECTED;
        }
        if (chunk == null || !offer.fileId().equals(chunk.fileId())) {          // 2
            return ChunkResult.REJECTED;
        }
        int index = chunk.index();
        if (index < 0 || index >= offer.totalChunks()) {                         // 3
            return ChunkResult.REJECTED;
        }
        if (chunk.data() == null || chunk.data().length != expectedLength(index)) { // 4
            return ChunkResult.REJECTED;
        }
        if (received.get(index)) {                                               // 5
            return ChunkResult.DUPLICATE;
        }

        long position = (long) index * offer.chunkSize();                        // 6
        ByteBuffer buffer = ByteBuffer.wrap(chunk.data());
        try {
            while (buffer.hasRemaining()) {
                channel.write(buffer, position + buffer.position());
            }
        } catch (IOException e) {
            cancel();
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong ghi duoc chunk " + index, e);
        }

        received.set(index);                                                     // 7
        receivedCount++;
        return ChunkResult.ACCEPTED;
    }

    public FileOfferPayload offer() {
        return offer;
    }

    public synchronized boolean isComplete() {
        return receivedCount == offer.totalChunks();
    }

    public synchronized int receivedChunks() {
        return receivedCount;
    }

    /**
     * Gia tri {@code contiguousUpTo} cua FILE_ACK (D4): moi chunk co
     * {@code index < contiguousChunks()} deu da nhan.
     */
    public synchronized int contiguousChunks() {
        return Math.min(received.nextClearBit(0), offer.totalChunks());
    }

    /**
     * Gia tri {@code receivedAbove} cua FILE_ACK (D4): cac chunk da nhan le phia
     * tren {@link #contiguousChunks()}, toi da {@code limit} phan tu.
     */
    public synchronized List<Integer> receivedAbove(int limit) {
        List<Integer> above = new ArrayList<>();
        int contiguous = contiguousChunks();
        for (int i = received.nextSetBit(contiguous + 1);
             i >= 0 && above.size() < limit;
             i = received.nextSetBit(i + 1)) {
            above.add(i);
        }
        return above;
    }

    /**
     * Da nhan du chunk: kiem SHA-256 ca file roi doi ten {@code .part} thanh file that.
     *
     * @return duong dan file da luu (khong ghi de file co san)
     * @throws IllegalStateException neu chua nhan du chunk hoac da ket thuc
     * @throws FileTransferException {@code CHECKSUM_MISMATCH} (file tam bi xoa) hoac {@code IO_ERROR}
     */
    public synchronized Path complete() {
        if (finished) {
            throw new IllegalStateException("Lan truyen file da ket thuc");
        }
        if (!isComplete()) {
            throw new IllegalStateException(
                    "Moi nhan " + receivedCount + "/" + offer.totalChunks() + " chunk");
        }

        // Windows khong cho doi ten / xoa file dang mo -> dong truoc
        closeQuietly(channel);
        finished = true;

        String actualHash;
        try {
            actualHash = Sha256.ofFile(partFile);
        } catch (IOException e) {
            deleteQuietly(partFile);
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong doc lai duoc file tam", e);
        }
        if (!actualHash.equals(offer.sha256())) {
            deleteQuietly(partFile);
            throw new FileTransferException(FileTransferException.Reason.CHECKSUM_MISMATCH,
                    "SHA-256 khong khop: mong doi " + offer.sha256() + ", nhan duoc " + actualHash);
        }

        try {
            return moveToUniqueTarget();
        } catch (IOException e) {
            deleteQuietly(partFile);
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong luu duoc file " + safeName, e);
        }
    }

    /** Huy giua chung: xoa file tam; cac chunk den sau se bi {@code REJECTED}. */
    public synchronized void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        closeQuietly(channel);
        deleteQuietly(partFile);
    }

    /** Dong khi chua hoan tat = huy. */
    @Override
    public synchronized void close() {
        cancel();
    }

    private Path moveToUniqueTarget() throws IOException {
        for (int attempt = 1; ; attempt++) {
            Path target = FileNames.uniqueTarget(directory, safeName);
            try {
                // KHONG dung REPLACE_EXISTING - khong bao gio ghi de file cua nguoi dung
                return Files.move(partFile, target);
            } catch (FileAlreadyExistsException e) {
                // Co file trung ten vua xuat hien giua luc kiem tra va luc doi ten -> thu ten khac
                if (attempt >= MAX_MOVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private int expectedLength(int index) {
        long offset = (long) index * offer.chunkSize();
        return (int) Math.min(offer.chunkSize(), offer.fileSize() - offset);
    }

    private static FileTransferException invalidOffer(String detail) {
        return new FileTransferException(FileTransferException.Reason.INVALID_OFFER,
                "FILE_OFFER khong hop le: " + detail);
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
            // dang don dep
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // dang don dep
        }
    }
}
