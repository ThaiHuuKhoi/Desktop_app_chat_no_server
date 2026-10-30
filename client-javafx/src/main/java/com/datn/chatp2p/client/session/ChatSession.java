package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;

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

    /** Roi phong, dong moi ket noi. Goi nhieu lan khong sao. */
    void leave();
}
