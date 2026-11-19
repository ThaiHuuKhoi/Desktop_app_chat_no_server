
package com.datn.chatp2p.p2p.filetransfer;

public record FileTransferLimits(long maxFileSize, int chunkSize) {

    // Dung lượng tệp tối đa mặc định: 100 MB
    public static final long DEFAULT_MAX_FILE_SIZE = 100L * 1024 * 1024;

    // Kích thước chunk mặc định: 4 KB
    public static final int DEFAULT_CHUNK_SIZE = 4 * 1024;

    // Kích thước chunk tối đa: 32 KB
    public static final int MAX_CHUNK_SIZE = 32 * 1024;

    // Cấu hình truyền tệp mặc định
    public static final FileTransferLimits DEFAULT =
            new FileTransferLimits(
                    DEFAULT_MAX_FILE_SIZE,
                    DEFAULT_CHUNK_SIZE
            );

    /**
     * Constructor kiểm tra tính hợp lệ của cấu hình.
     */
    public FileTransferLimits {

        // Dung lượng tệp không được âm
        if (maxFileSize < 0) {
            throw new IllegalArgumentException(
                    "maxFileSize must not be negative"
            );
        }

        // Kích thước chunk phải nằm trong khoảng 1..MAX_CHUNK_SIZE
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "chunkSize must be between 1 and "
                            + MAX_CHUNK_SIZE
            );
        }
    }

    /**
     * Tính tổng số chunk cần thiết để truyền một tệp.
     *
     * @param fileSize kích thước tệp (byte)
     * @param chunkSize kích thước mỗi chunk (byte)
     * @return tổng số chunk
     */
    public static int totalChunks(long fileSize, int chunkSize) {

        // Kiểm tra kích thước tệp
        if (fileSize < 0) {
            throw new IllegalArgumentException(
                    "fileSize must not be negative"
            );
        }

        // Kiểm tra kích thước chunk
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid chunkSize"
            );
        }

        // Chia lấy phần nguyên và làm tròn lên
        // Tránh tràn số khi cộng fileSize + chunkSize - 1
        long total = fileSize / chunkSize
                + (fileSize % chunkSize == 0 ? 0 : 1);

        // Chuyển long sang int an toàn
        return Math.toIntExact(total);
    }
}
