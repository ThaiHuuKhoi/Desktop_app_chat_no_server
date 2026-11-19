package com.datn.chatp2p.p2p.filetransfer;

import com.datn.chatp2p.common.protocol.FileChunkPayload;
import com.datn.chatp2p.common.protocol.FileOfferPayload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Ben gui: tao FILE_OFFER cho 1 file va doc tung chunk theo yeu cau
 * (docs/Thong-nhat-giao-thuc-truyen-file.md D1, D3, D5).
 *
 * <p>Khong nap ca file vao RAM: moi lan {@link #readChunk(int)} chi doc dung
 * 1 chunk tai vi tri cua no.</p>
 */
public final class FileSender implements ChunkSource, AutoCloseable {

    private final FileChannel channel;
    private final FileOfferPayload offer;

    private FileSender(FileChannel channel, FileOfferPayload offer) {
        this.channel = channel;
        this.offer = offer;
    }

    public static FileSender open(Path file) {
        return open(file, FileTransferLimits.DEFAULT);
    }

    /**
     * @throws IllegalArgumentException neu {@code file} khong phai file thuong
     * @throws FileTransferException    {@code FILE_TOO_LARGE} hoac {@code IO_ERROR}
     */
    public static FileSender open(Path file, FileTransferLimits limits) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(limits, "limits");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Khong phai file thuong: " + file);
        }

        FileChannel channel = null;
        try {
            channel = FileChannel.open(file, StandardOpenOption.READ);
            long size = channel.size();
            if (size > limits.maxFileSize()) {
                throw new FileTransferException(FileTransferException.Reason.FILE_TOO_LARGE,
                        "File " + size + " byte vuot gioi han " + limits.maxFileSize() + " byte");
            }

            int chunkSize = limits.chunkSize();
            FileOfferPayload offer = new FileOfferPayload(
                    UUID.randomUUID().toString(),
                    file.getFileName().toString(),
                    size,
                    probeMimeType(file),
                    chunkSize,
                    FileTransferLimits.totalChunks(size, chunkSize),
                    Sha256.ofFile(file));
            return new FileSender(channel, offer);
        } catch (IOException e) {
            closeQuietly(channel);
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong doc duoc file " + file.getFileName(), e);
        } catch (RuntimeException e) {
            closeQuietly(channel);
            throw e;
        }
    }

    @Override
    public FileOfferPayload offer() {
        return offer;
    }

    /**
     * {@code synchronized}: phan cua A co the goi tu luong hen gio gui lai
     * cung luc voi luong gui chinh.
     */
    @Override
    public synchronized FileChunkPayload readChunk(int index) {
        Objects.checkIndex(index, offer.totalChunks());

        long offset = (long) index * offer.chunkSize();                           // ep long TRUOC khi nhan
        int length = (int) Math.min(offer.chunkSize(), offer.fileSize() - offset); // chunk cuoi ngan hon
        ByteBuffer buffer = ByteBuffer.allocate(length);
        try {
            // read(buffer, position) khong dich con tro file -> doc lai chunk cu an toan.
            // 1 lan read co the doc it hon yeu cau -> lap cho toi khi du.
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer, offset + buffer.position());
                if (n < 0) {
                    throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                            "File bi ngan di trong luc gui (chunk " + index + ")");
                }
            }
        } catch (IOException e) {
            throw new FileTransferException(FileTransferException.Reason.IO_ERROR,
                    "Khong doc duoc chunk " + index, e);
        }
        return new FileChunkPayload(offer.fileId(), index, buffer.array());
    }

    @Override
    public synchronized void close() {
        closeQuietly(channel);
    }

    private static String probeMimeType(Path file) {
        try {
            return Files.probeContentType(file); // co the null - D1 cho phep
        } catch (IOException e) {
            return null;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // dang don dep - khong co gi de lam them
        }
    }
}
