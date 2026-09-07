package com.datn.chatp2p.p2p.channel;

import com.datn.chatp2p.common.channel.DataChannel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Cai dat that cua {@link DataChannel} tren 1 {@link DatagramSocket} UDP -
 * Tai-lieu-ky-thuat.md Phan E.6.3. Socket va dia chi dich phai da duoc chon
 * xong boi ICE (candidate pair da "thong" sau connectivity check) truoc khi
 * tao doi tuong nay - lop nay khong tu lam ICE, chi lo gui/nhan byte tren
 * duong truyen da co san.
 *
 * <p><b>Framing:</b> them 4-byte length-prefix truoc moi goi tin. Voi UDP thuan
 * dieu nay khong bat buoc (1 loi goi {@code send()} da anh xa dung 1 goi tin,
 * UDP tu giu ranh gioi) nhung van giu de dong bo voi thiet ke chung, phong khi
 * sau nay doi sang TCP/DTLS-over-UDP ma khong phai doi lai giao thuc dong goi.
 *
 * <p><b>Gioi han da biet</b> (chua xu ly trong ban dau nay, xem
 * Tai-lieu-ky-thuat.md Phan H.3): UDP khong dam bao thu tu/khong mat goi - chua
 * co ACK/retry; chua tu phat hien "peer mat ket noi" qua timeout khong nhan
 * duoc goi tin nao.
 */
public final class P2pDataChannel implements DataChannel {

    private static final int MAX_UDP_PAYLOAD = 65_507; // gioi han thuc te cua 1 datagram IPv4
    private static final int LENGTH_PREFIX_BYTES = 4;

    private final DatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final ExecutorService receiveLoopExecutor;
    private volatile Consumer<byte[]> receiveHandler;
    private volatile boolean closed;

    /**
     * @param socket        socket UDP da duoc ICE chon (vi du lay tu
     *                      {@code Component.getSocket()} cua ice4j sau khi
     *                      {@code IceProcessingState.COMPLETED}).
     * @param remoteAddress dia chi/cong cua candidate pair da duoc chon o phia doi phuong.
     */
    public P2pDataChannel(DatagramSocket socket, InetSocketAddress remoteAddress) {
        this.socket = socket;
        this.remoteAddress = remoteAddress;
        this.receiveLoopExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "p2p-datachannel-receive");
            thread.setDaemon(true);
            return thread;
        });
        receiveLoopExecutor.submit(this::runReceiveLoop);
    }

    @Override
    public void send(byte[] data) {
        if (closed) {
            throw new IllegalStateException("DataChannel da bi dong");
        }
        byte[] framed = frame(data);
        // Kiem tra RO RANG truoc khi goi socket.send() - neu khong, OS se tu choi
        // voi 1 IOException "Message too long" chung chung, bi boc lai thanh
        // UncheckedIOException KHONG noi ro nguyen nhan la payload qua lon (da
        // xac nhan that: thong diep loi mac dinh chi la "Gui du lieu qua
        // P2pDataChannel that bai", phai tu dao sau vao getCause() moi biet).
        // Quan trong hon: gioi han 65.507 byte nay KHONG PHAI gioi han thuc te
        // cho payload UNG DUNG - qua EnvelopeCodec (JSON + base64 hoa truong
        // byte[] + AES-GCM), gioi han thuc te cho vi du 1 tin nhan van ban chi
        // con khoang ~49KB. Bao loi som, ro rang o day de ai do lam tinh nang
        // chia nho file/media (FILE_CHUNK/MEDIA_FRAME) sau nay biet chinh xac
        // can chia nho toi dau, khong phai tu doan hay tu gap loi kho hieu.
        if (framed.length > MAX_UDP_PAYLOAD) {
            throw new IllegalArgumentException(
                    "Payload qua lon de gui qua 1 goi UDP: " + framed.length + " byte (da gom "
                            + LENGTH_PREFIX_BYTES + " byte length-prefix), vuot qua gioi han "
                            + MAX_UDP_PAYLOAD + " byte cua 1 datagram IPv4. Du lieu dau vao cho send() "
                            + "toi da " + (MAX_UDP_PAYLOAD - LENGTH_PREFIX_BYTES) + " byte - luu y day la"
                            + " du lieu SAU KHI da qua EnvelopeCodec (JSON+base64+AES-GCM), nen payload"
                            + " ung dung goc (vd noi dung tin nhan) can nho hon nhieu; voi du lieu lon hon"
                            + " (vd file/media) phai tu chia nho (chunk) truoc khi goi send().");
        }
        try {
            socket.send(new DatagramPacket(framed, framed.length, remoteAddress));
        } catch (IOException e) {
            throw new UncheckedIOException("Gui du lieu qua P2pDataChannel that bai", e);
        }
    }

    @Override
    public void onReceive(Consumer<byte[]> handler) {
        this.receiveHandler = handler;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        socket.close();
        receiveLoopExecutor.shutdownNow();
    }

    private void runReceiveLoop() {
        byte[] buffer = new byte[MAX_UDP_PAYLOAD];
        while (!closed) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (SocketException e) {
                // socket.close() tu ben ngoai (close()) lam receive() nem loi nay - binh thuong khi dong.
                break;
            } catch (IOException e) {
                if (closed) {
                    break;
                }
                // Loi mang khac khi chua chu dong dong - bo qua goi tin nay, thu nhan tiep.
                continue;
            }

            // QUAN TRONG (bao mat): socket nay KHONG duoc connect() toi remoteAddress
            // (xem ly do o javadoc lop nay/isFromExpectedRemote) nen socket.receive()
            // chap nhan goi tin tu BAT KY nguon nao gui toi dung cong nay, khong chi
            // tu peer da thoa thuan qua ICE. Ket hop voi viec goi tin DAU TIEN (public
            // key ECDH, xem PeerConnection) chua ma hoa, 1 ke tan cong biet duoc cong
            // UDP nay (dai cong ICE 10000-11000 kha hep, diem bat dau moi Agent lai
            // XOAY VONG - xem IceP2pConnectionEstablisher - nen kha doan duoc) co the
            // gui 1 "public key" gia mao TOI TRUOC peer that, chiem quyen bat tay ECDH
            // (tan cong MITM/key-substitution kinh dien cho Diffie-Hellman khong xac
            // thuc). Loc bo goi tin tu nguon KHONG phai remoteAddress da duoc ICE chon
            // la 1 lop phong thu them (khong the chan tuyet doi ke tan cong ON-PATH co
            // the gia mao dia chi nguon, nhung chan duoc ke tan cong OFF-PATH/quet cong
            // ngau nhien khong biet chinh xac dia chi that cua peer hop le) - xac thuc
            // TRIET DE van can PEER_IDENTITY (Tai-lieu-ky-thuat.md, can
            // IdentitySignatureService cua B, chua co - xem docs/Bao-cao-thuc-hien-Nhiem-vu-A.md
            // muc "Chua lam").
            if (!isFromExpectedRemote(packet)) {
                continue;
            }

            byte[] data;
            try {
                data = unframe(packet.getData(), packet.getLength());
            } catch (RuntimeException malformed) {
                // Goi tin khong dung dinh dang length-prefix - bo qua, khong lam chet vong lap nhan.
                continue;
            }

            Consumer<byte[]> handler = receiveHandler;
            if (handler != null) {
                try {
                    handler.accept(data);
                } catch (RuntimeException handlerFailure) {
                    // QUAN TRONG: handler (thuong la PeerConnection.handleIncoming, giai ma
                    // AES-GCM) co the nem loi voi 1 goi tin da qua duoc kiem tra framing o
                    // tren nhung noi dung ben trong hong/gia mao (vd sai khoa, du lieu bi
                    // thay doi tren duong truyen, hoac 1 goi UDP la lam roi vao dung cong
                    // nay tinh co) - neu khong bat o day, loi se thoat ra khoi vong lap
                    // while nay, LAM CHET VINH VIEN thread nhan cua CHINH kenh nay (vi day
                    // la than cua vong lap - khong con ai goi lai socket.receive() nua) -
                    // ket noi P2P se tro thanh "xac song": tuong con mo nhung khong bao gio
                    // nhan duoc gi nua. Dung nguyen tac da ap dung xuyen suot du an (Tai-lieu-ky-thuat.md
                    // Phan H.1): 1 goi tin loi khong duoc lam gian doan viec nhan cac goi
                    // tin sau do - bo qua dung goi tin nay, tiep tuc vong lap.
                    continue;
                }
            }
        }
    }

    /**
     * {@code true} neu {@code packet} den tu DUNG dia chi/cong da duoc ICE
     * chon ({@link #remoteAddress}) - xem binh luan bao mat o {@link #runReceiveLoop()}.
     * So sanh qua {@link InetAddress#equals} (dia chi IP) va port rieng (khong
     * dung {@code SocketAddress.equals} de tranh phu thuoc vao kieu con cu the
     * cua {@code InetSocketAddress} tra ve tu {@code DatagramPacket}).
     */
    private boolean isFromExpectedRemote(DatagramPacket packet) {
        return packet.getPort() == remoteAddress.getPort()
                && packet.getAddress().equals(remoteAddress.getAddress());
    }

    private static byte[] frame(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(LENGTH_PREFIX_BYTES + data.length);
        buffer.putInt(data.length);
        buffer.put(data);
        return buffer.array();
    }

    private static byte[] unframe(byte[] raw, int length) {
        if (length < LENGTH_PREFIX_BYTES) {
            throw new IllegalArgumentException("Goi tin qua ngan de chua length-prefix");
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw, 0, length);
        int dataLength = buffer.getInt();
        if (dataLength < 0 || dataLength > length - LENGTH_PREFIX_BYTES) {
            throw new IllegalArgumentException("Length-prefix khong khop voi kich thuoc goi tin thuc te");
        }
        byte[] data = new byte[dataLength];
        buffer.get(data);
        return data;
    }
}
