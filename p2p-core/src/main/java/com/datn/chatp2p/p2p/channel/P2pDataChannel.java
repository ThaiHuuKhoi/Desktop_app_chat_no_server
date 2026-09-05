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

            byte[] data;
            try {
                data = unframe(packet.getData(), packet.getLength());
            } catch (RuntimeException malformed) {
                // Goi tin khong dung dinh dang length-prefix - bo qua, khong lam chet vong lap nhan.
                continue;
            }

            Consumer<byte[]> handler = receiveHandler;
            if (handler != null) {
                handler.accept(data);
            }
        }
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
