package com.datn.chatp2p.common.channel;

import java.util.function.Consumer;

/**
 * Interface chung giua module p2p-core (Thanh vien A) va client-javafx / crypto
 * (Thanh vien B) - da chot trong Phan-cong-cong-viec.md muc 3.
 *
 * <p>Ca hai phia code doc lap tren interface nay:
 * <ul>
 *   <li>{@code com.datn.chatp2p.p2p.channel.P2pDataChannel} - ban that, chay tren
 *       ket noi ICE/socket that (Thanh vien A cai dat, se hoan thien o tuan 7-9).</li>
 *   <li>{@code com.datn.chatp2p.p2p.LoopbackDataChannel} - ban gia lap chay noi bo
 *       trong cung may, du de dung UI va ma hoa ma khong can cho phan mang xong.</li>
 * </ul>
 *
 * <p>Den tuan tich hop, chi can thay {@code LoopbackDataChannel} bang
 * {@code P2pDataChannel} that - logic ma hoa/UI o tang tren khong doi.
 */
public interface DataChannel {

    /**
     * Gui du lieu tho (da duoc ma hoa boi tang tren, neu can) toi dau ben kia.
     */
    void send(byte[] data);

    /**
     * Dang ky handler duoc goi moi khi nhan duoc mot goi du lieu tho tu dau ben kia.
     */
    void onReceive(Consumer<byte[]> handler);

    /**
     * Dong kenh, giai phong tai nguyen lien quan.
     */
    void close();
}
