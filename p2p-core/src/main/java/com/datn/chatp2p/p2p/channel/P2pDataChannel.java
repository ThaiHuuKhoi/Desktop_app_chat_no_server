package com.datn.chatp2p.p2p.channel;

import com.datn.chatp2p.common.channel.DataChannel;

import java.util.function.Consumer;

/**
 * Khung viec cho ket noi P2P THAT giua hai may, sau khi da thiet lap duong
 * truyen bang ICE (uu tien ket noi truc tiep, du phong TURN relay khi NAT
 * chan) - De-cuong-Chat-P2P-Java.md muc 5-6, giai doan 4 (tuan 7-9).
 *
 * <p><b>TODO (Thanh vien A, tuan 7-9):</b>
 * <ol>
 *   <li>Dung ice4j de gather ICE candidate, trao doi qua
 *       {@link com.datn.chatp2p.p2p.signaling.SignalingClient}, chay ICE
 *       connectivity checks de tim duong truyen kha dung.</li>
 *   <li>Sau khi ICE thanh cong, mo mot kenh du lieu (vi du UDP/DTLS hoac TCP
 *       socket thuan tren dia chi/cong ma ICE da chon) va cai dat
 *       {@link #send}/{@link #onReceive}/{@link #close} tren kenh do.</li>
 *   <li>Them framing (do dai goi tin) va co che retry/handle mat ket noi -
 *       xem muc "Giao thuc dong goi du lieu" trong Phan-cong-cong-viec.md.</li>
 * </ol>
 *
 * <p>Cho toi khi hoan thien, cac method deu nem {@link UnsupportedOperationException}
 * de module van bien dich duoc va cac module khac (client-javafx) co the phu
 * thuoc vao lop nay ma khong bi chan boi loi bien dich.
 */
public class P2pDataChannel implements DataChannel {

    @Override
    public void send(byte[] data) {
        throw new UnsupportedOperationException(
                "P2pDataChannel chua duoc cai dat - xem TODO tuan 7-9 (ice4j + socket that). "
                        + "Dung LoopbackDataChannel de demo/phat trien UI trong luc cho.");
    }

    @Override
    public void onReceive(Consumer<byte[]> handler) {
        throw new UnsupportedOperationException(
                "P2pDataChannel chua duoc cai dat - xem TODO tuan 7-9 (ice4j + socket that).");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException(
                "P2pDataChannel chua duoc cai dat - xem TODO tuan 7-9 (ice4j + socket that).");
    }
}
