package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;

/**
 * Diem noi DUY NHAT giua giao dien ({@code client-javafx}) va tang mang/ma hoa.
 * Giao dien chi biet interface nay, khong goi truc tiep {@code AesGcmCipher},
 * {@code DataChannel} hay {@code RoomSession} - dung huong Tai-lieu-ky-thuat.md
 * muc E.10 (bo viec UI tu goi ma hoa).
 *
 * <p>Hai cai dat:
 * <ul>
 *   <li>{@link DemoChatSession} - peer gia lap qua {@code LoopbackDataChannel},
 *       ma hoa that (ECDH + AES-GCM), khong can mang.</li>
 *   <li>Ban that (tich hop voi {@code RoomSession} cua p2p-core) - chi can them
 *       1 lop moi va dang ky trong {@link ChatSessions#create}:
 *       <ul>
 *         <li>{@link #start}: dang ky {@code roomSession.onPeerJoined / onPeerLeft /
 *             onConnectionFailed / onEnvelope(MESSAGE, ...)} roi goi {@code join(serverUri)}.</li>
 *         <li>{@link #sendGroupMessage}: {@code roomSession.broadcast(EnvelopeType.MESSAGE,
 *             new MessagePayload(id, authorId, text, timeSent))}.</li>
 *         <li>{@link #leave}: {@code roomSession.leave()}.</li>
 *         <li>De hien van tay va kieu ket noi trong {@link SessionPeer}, can
 *             {@code PeerConnection} cung cap them public key ECDH cua 2 ben va
 *             thong tin truc tiep/relay (tu {@code IceConnectionStats}).</li>
 *       </ul>
 *   </li>
 * </ul>
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

    /** Roi phong, dong moi ket noi. Goi nhieu lan khong sao. */
    void leave();
}
