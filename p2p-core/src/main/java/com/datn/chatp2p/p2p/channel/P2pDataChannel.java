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
 * <p><b>Framing va chia manh:</b> byte dau moi datagram cho biet loai: {@code 1} = ban tin nguyen,
 * {@code 2} = 1 manh cua ban tin lon (id ban tin, so thu tu, tong so manh). Ban tin lon hon
 * {@value #MAX_FRAGMENT_DATA} byte duoc chia nho va ghep lai o ben nhan, vi ice4j cat bo moi goi UDP
 * lon hon 1500 byte. Mat 1 manh la mat ca ban tin (tang tren tu gui lai neu can).
 *
 * <p><b>Gioi han da biet</b> (chua xu ly trong ban dau nay, xem
 * Tai-lieu-ky-thuat.md Phan H.3): UDP khong dam bao thu tu/khong mat goi - chua
 * co ACK/retry; chua tu phat hien "peer mat ket noi" qua timeout khong nhan
 * duoc goi tin nao.
 */
public final class P2pDataChannel implements DataChannel {

    private static final int MAX_UDP_PAYLOAD = 65_507; // gioi han thuc te cua 1 datagram IPv4 (bo dem nhan)
    /** Xem javadoc cua {@link #enlargeSocketBuffers} - 1MB, du cho hang nghin Envelope nho don dap. */
    private static final int SOCKET_BUFFER_SIZE_BYTES = 1_048_576;

    private final DatagramSocket socket;
    private final InetSocketAddress remoteAddress;
    private final ExecutorService receiveLoopExecutor;
    private volatile Consumer<byte[]> receiveHandler;
    private volatile boolean closed;

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(P2pDataChannel.class.getName());
    /** So dong log "goi bi bo qua" da ghi cho kenh nay - xem {@link #logDropped}. */
    private final java.util.concurrent.atomic.AtomicInteger droppedLogged = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * @param socket        socket UDP da duoc ICE chon (vi du lay tu
     *                      {@code Component.getSocket()} cua ice4j sau khi
     *                      {@code IceProcessingState.COMPLETED}).
     * @param remoteAddress dia chi/cong cua candidate pair da duoc chon o phia doi phuong.
     */
    public P2pDataChannel(DatagramSocket socket, InetSocketAddress remoteAddress) {
        this.socket = socket;
        this.remoteAddress = remoteAddress;
        enlargeSocketBuffers(socket);
        this.receiveLoopExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "p2p-datachannel-receive");
            thread.setDaemon(true);
            return thread;
        });
        receiveLoopExecutor.submit(this::runReceiveLoop);
    }

    /**
     * Tang buffer nhan/gui cua {@code socket} len {@link #SOCKET_BUFFER_SIZE_BYTES}
     * (mac dinh cua OS thuong CHI vai chuc KB - qua nho so voi 1 "dot" nhieu
     * Envelope gui lien tiep khong co khoang nghi). Xac nhan that bang test
     * {@code PeerConnectionBurstThroughputTest}: LAN DAU (chua tang buffer) gui
     * 1000 Envelope lien tiep tren CHINH localhost (khong co tac nghen mang
     * that), chi 358/1000 (35.8%) toi noi - vi Envelope THUONG (khac goi ECDH
     * public key ban dau, xem PeerConnection#sendEcdhPublicKey) hoan toan KHONG
     * co ACK/retry, 1 goi bi mat vi buffer day la mat VINH VIEN. Khong the
     * loai bo hoan toan rui ro nay (UDP von khong dam bao) nhung tang buffer
     * giup vong lap nhan (dang xu ly tuan tu: giai ma AES-GCM + parse JSON +
     * goi handler cho TUNG goi mot) co du "khong gian dem" de bat kip 1 dot
     * gui don dap, thay vi OS am tham vut bo ngay khi buffer qua nho day len.
     *
     * <p>Khong nem loi neu OS tu choi (vd moi truong bi gioi han) - chi cai
     * dat "co gang het suc", ban than P2pDataChannel van hoat dong dung voi
     * buffer mac dinh cua OS, chi kem chiu tai hon.
     */
    private static void enlargeSocketBuffers(DatagramSocket socket) {
        try {
            socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE_BYTES);
            socket.setSendBufferSize(SOCKET_BUFFER_SIZE_BYTES);
        } catch (SocketException e) {
            // OS tu choi cap phat buffer lon nhu yeu cau - bo qua, van tiep tuc
            // voi bat ky gia tri nao OS da chap nhan (thuong la gia tri mac dinh).
        }
    }

    @Override
    public void send(byte[] data) {
        if (closed) {
            throw new IllegalStateException("DataChannel da bi dong");
        }
        // Ban tin lon duoc chia thanh nhieu datagram nho (xem MAX_FRAGMENT_DATA); bao loi ro rang neu
        // vuot gioi han tong de nguoi goi biet phai tu chia nho (vd file -> chunk).
        if (data.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "Payload qua lon de gui qua P2pDataChannel: " + data.length + " byte, vuot gioi han "
                            + MAX_MESSAGE_BYTES + " byte cho 1 ban tin (du lieu SAU KHI da qua EnvelopeCodec:"
                            + " JSON+base64+AES-GCM). Du lieu lon hon (vd file/media) phai tu chia nho truoc khi gui.");
        }
        try {
            for (byte[] frame : frames(data)) {
                socket.send(new DatagramPacket(frame, frame.length, remoteAddress));
            }
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
                logDropped("goi tu dia chi la " + packet.getAddress() + ":" + packet.getPort());
                continue;
            }

            byte[] data;
            try {
                data = accept(packet.getData(), packet.getLength());
            } catch (RuntimeException malformed) {
                // Goi tin sai dinh dang - bo qua, khong lam chet vong lap nhan.
                logDropped("goi sai dinh dang (" + packet.getLength() + " byte)");
                continue;
            }
            if (data == null) {
                continue; // manh cua ban tin lon, con cho cac manh khac
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
                    logDropped("khong giai ma/xu ly duoc (" + data.length + " byte): " + handlerFailure.getMessage());
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
    /**
     * Log goi bi bo qua o muc FINE (chi hien khi bat debug) va gioi han 20 dong/kenh - ke tan cong
     * spam goi rac khong lam day terminal.
     */
    private void logDropped(String reason) {
        int count = droppedLogged.incrementAndGet();
        if (count <= 20) {
            LOG.fine(() -> "Bo qua goi UDP tu " + remoteAddress + ": " + reason
                    + (count == 20 ? " (da dat 20 dong, ngung log goi bi bo qua cua kenh nay)" : ""));
        }
    }

    private boolean isFromExpectedRemote(DatagramPacket packet) {
        return packet.getPort() == remoteAddress.getPort()
                && packet.getAddress().equals(remoteAddress.getAddress());
    }

    // ------------------------------------------------------------ chia manh

    /**
     * Du lieu toi da trong 1 datagram. ice4j ({@code MergingDatagramSocket}) nhan moi goi vao bo dem
     * CO DINH 1500 byte - goi lon hon bi cat va bi bo am tham (da do that khi truyen file). Giu moi
     * datagram &lt;= ~1170 byte (con cho cho header UDP/IP va TURN, va duoi MTU toi thieu 1280 cua IPv6).
     */
    static final int MAX_FRAGMENT_DATA = 1150;
    /** 1 ban tin lon nhat sau khi ghep lai (chong ban tin rac chiem bo nho). */
    static final int MAX_MESSAGE_BYTES = 1_048_576;
    /** Toi da so ban tin dang ghep do dang cung luc, va thoi gian cho manh con thieu. */
    static final int MAX_PENDING_MESSAGES = 64;
    static final long FRAGMENT_TIMEOUT_MILLIS = 5_000;

    private static final byte TYPE_WHOLE = 1;
    private static final byte TYPE_FRAGMENT = 2;
    /** type(1) + id ban tin(4) + so thu tu manh(2) + tong so manh(2). */
    private static final int FRAGMENT_HEADER_BYTES = 9;

    private final java.util.concurrent.atomic.AtomicInteger nextMessageId = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.Map<Integer, Reassembly> pending = new java.util.LinkedHashMap<>();

    /** Cac datagram can gui cho {@code data}: 1 goi nguyen neu vua, nguoc lai chia manh. */
    java.util.List<byte[]> frames(byte[] data) {
        if (data.length + 1 <= MAX_FRAGMENT_DATA + FRAGMENT_HEADER_BYTES) {
            byte[] whole = new byte[1 + data.length];
            whole[0] = TYPE_WHOLE;
            System.arraycopy(data, 0, whole, 1, data.length);
            return java.util.List.of(whole);
        }
        int count = (data.length + MAX_FRAGMENT_DATA - 1) / MAX_FRAGMENT_DATA;
        int messageId = nextMessageId.getAndIncrement();
        java.util.List<byte[]> result = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * MAX_FRAGMENT_DATA;
            int length = Math.min(MAX_FRAGMENT_DATA, data.length - from);
            ByteBuffer buffer = ByteBuffer.allocate(FRAGMENT_HEADER_BYTES + length);
            buffer.put(TYPE_FRAGMENT).putInt(messageId).putShort((short) index).putShort((short) count);
            buffer.put(data, from, length);
            result.add(buffer.array());
        }
        return result;
    }

    /** Nhan 1 datagram; tra ve ban tin day du (hoac null neu con cho manh khac). */
    byte[] accept(byte[] raw, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Goi rong");
        }
        if (raw[0] == TYPE_WHOLE) {
            return java.util.Arrays.copyOfRange(raw, 1, length);
        }
        if (raw[0] != TYPE_FRAGMENT || length <= FRAGMENT_HEADER_BYTES) {
            throw new IllegalArgumentException("Goi sai dinh dang");
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw, 1, length - 1);
        int messageId = buffer.getInt();
        int index = Short.toUnsignedInt(buffer.getShort());
        int count = Short.toUnsignedInt(buffer.getShort());
        if (count < 2 || index >= count || (long) count * MAX_FRAGMENT_DATA > MAX_MESSAGE_BYTES + MAX_FRAGMENT_DATA) {
            throw new IllegalArgumentException("Thong so manh khong hop le");
        }
        byte[] part = java.util.Arrays.copyOfRange(raw, FRAGMENT_HEADER_BYTES, length);
        long now = System.currentTimeMillis();
        synchronized (pending) {
            pending.values().removeIf(r -> now - r.startedAt > FRAGMENT_TIMEOUT_MILLIS);
            Reassembly reassembly = pending.get(messageId);
            if (reassembly == null) {
                if (pending.size() >= MAX_PENDING_MESSAGES) {
                    pending.remove(pending.keySet().iterator().next()); // bo ban tin cu nhat
                }
                reassembly = new Reassembly(count, now);
                pending.put(messageId, reassembly);
            } else if (reassembly.parts.length != count) {
                throw new IllegalArgumentException("So manh khong khop");
            }
            byte[] complete = reassembly.add(index, part);
            if (complete != null) {
                pending.remove(messageId);
            }
            return complete;
        }
    }

    private static final class Reassembly {
        final byte[][] parts;
        final long startedAt;
        int received;
        int totalBytes;

        Reassembly(int count, long startedAt) {
            this.parts = new byte[count][];
            this.startedAt = startedAt;
        }

        byte[] add(int index, byte[] part) {
            if (parts[index] != null) {
                return null; // manh trung
            }
            parts[index] = part;
            received++;
            totalBytes += part.length;
            if (received < parts.length) {
                return null;
            }
            byte[] message = new byte[totalBytes];
            int offset = 0;
            for (byte[] piece : parts) {
                System.arraycopy(piece, 0, message, offset, piece.length);
                offset += piece.length;
            }
            return message;
        }
    }
}
