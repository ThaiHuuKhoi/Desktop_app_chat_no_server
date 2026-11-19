package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;

import java.nio.file.Path;

/**
 * Diem noi DUY NHAT giua giao dien ({@code client-javafx}) va tang mang/ma hoa.
 * Giao dien chi biet interface nay, khong goi truc tiep {@code AesGcmCipher},
 * {@code DataChannel} hay {@code RoomSession} - dung huong Tai-lieu-ky-thuat.md
 * muc E.10 (bo viec UI tu goi ma hoa).
 *
 * <p>Implementation hien tai tich hop voi {@code RoomSession} cua p2p-core.
 *
 * <p>Moi callback cua {@link ChatSessionListener} co the duoc goi tu thread
 * mang - giao dien tu chuyen ve JavaFX thread.
 */
public interface ChatSession {

    /** Id cua chinh minh trong phong (tac gia cua cac tin minh gui). */
    String selfPeerId();

    /** Bat dau ket noi/vao phong; su kien sau do bao qua {@code listener}. */
    void start(ChatSessionListener listener);

    /**
     * Gui 1 tin nhan toi tat ca peer trong phong. Nem {@link RuntimeException}
     * neu gui that bai - giao dien phai bao cho nguoi dung, khong duoc im lang.
     */
    void sendGroupMessage(ChatMessage message);

    /** Gui tin nhan rieng toi mot peer dang ket noi. */
    default void sendDirectMessage(String peerId, ChatMessage message) {
        throw new UnsupportedOperationException("Che do nay chua ho tro tin nhan rieng");
    }

    /** Dung luong file toi da duoc gui / nhan (byte). */
    default long maxFileSize() {
        return 0;
    }

    /**
     * Gui 1 file toi 1 peer (1-1). Ham nay CHAN vi phai doc ca file de tinh
     * SHA-256 - giao dien phai goi tu luong nen. Tien do bao qua
     * {@link ChatSessionListener#onFileTransferUpdated(FileTransferStatus)}.
     *
     * @param direct {@code true} neu gui trong tab tin nhan rieng
     * @throws RuntimeException neu khong mo duoc file / file qua lon
     */
    default void sendFile(String peerId, Path file, boolean direct) {
        throw new UnsupportedOperationException("Che do nay chua ho tro gui file");
    }

    /** Chap nhan file duoc moi, luu vao {@code directory}. */
    default void acceptFile(String fileId, Path directory) {
        throw new UnsupportedOperationException("Che do nay chua ho tro nhan file");
    }

    /** Tu choi file duoc moi. */
    default void rejectFile(String fileId) {
        throw new UnsupportedOperationException("Che do nay chua ho tro nhan file");
    }

    /** Huy 1 lan truyen file dang cho / dang chay (ca gui va nhan). */
    default void cancelFile(String fileId) {
        throw new UnsupportedOperationException("Che do nay chua ho tro truyen file");
    }

    /** Roi phong, dong moi ket noi. Goi nhieu lan khong sao. */
    void leave();
}
