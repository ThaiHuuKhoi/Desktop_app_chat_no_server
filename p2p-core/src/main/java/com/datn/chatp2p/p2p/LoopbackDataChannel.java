package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Cai dat gia lap cua {@link DataChannel}, chay hoan toan trong bo nho cua
 * cung mot tien trinh - dung theo dung tinh than cua Phan-cong-cong-viec.md
 * muc 3: "Thanh vien B cai dat tam ban gia lap LoopbackDataChannel ... du de
 * dung UI va ma hoa ma khong can cho A xong phan mang".
 *
 * <p>Dung {@link #createPair()} de tao 2 dau ket noi voi nhau: du lieu gui o
 * dau nay se duoc giao cho handler cua dau kia (bat dong bo, tren mot thread
 * rieng, de mo phong dung hanh vi mang thuc). Den tuan tich hop, chi can thay
 * mot trong hai dau nay bang {@code P2pDataChannel} that.
 */
public final class LoopbackDataChannel implements DataChannel {

    /** Hai dau cua mot ket noi loopback, da duoc noi voi nhau. */
    public record Pair(DataChannel endpointA, DataChannel endpointB) {
    }

    /** Tao mot cap DataChannel gia lap, da noi san voi nhau. */
    public static Pair createPair() {
        ExecutorService deliveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "loopback-datachannel-delivery");
            thread.setDaemon(true);
            return thread;
        });

        LoopbackDataChannel endpointA = new LoopbackDataChannel(deliveryExecutor);
        LoopbackDataChannel endpointB = new LoopbackDataChannel(deliveryExecutor);
        endpointA.peer = endpointB;
        endpointB.peer = endpointA;

        return new Pair(endpointA, endpointB);
    }

    private final ExecutorService deliveryExecutor;
    private LoopbackDataChannel peer;
    private volatile Consumer<byte[]> receiveHandler;
    private volatile boolean closed;

    private LoopbackDataChannel(ExecutorService deliveryExecutor) {
        this.deliveryExecutor = deliveryExecutor;
    }

    @Override
    public void send(byte[] data) {
        if (closed) {
            throw new IllegalStateException("DataChannel da bi dong");
        }
        deliveryExecutor.execute(() -> {
            Consumer<byte[]> handler = peer.receiveHandler;
            if (handler != null && !peer.closed) {
                try {
                    handler.accept(data);
                } catch (RuntimeException e) {
                    // Giong het ly do o P2pDataChannel (ban that dung UDP): handler
                    // (thuong la PeerConnection.handleIncoming) co the nem loi voi
                    // 1 goi tin hop le ve mat van chuyen nhung noi dung khong con
                    // hop le nua - vi du ban sao GUI LAI cua public key ECDH
                    // (PeerConnection#sendEcdhPublicKey tu dong gui lai toi 5 lan)
                    // toi SAU KHI phia nhan da hoan tat handshake tu ban sao truoc
                    // do, se bi hieu nham la du lieu ma hoa va giai ma AES-GCM that
                    // bai. Day la tinh huong VO HAI, khong duoc de loi in ra console
                    // nhu 1 loi nghiem trong (truoc khi sua: ExecutorService tu thay
                    // worker thread chet nen kenh khong "chet han" nhu P2pDataChannel,
                    // nhung van in stack trace gay nham lan).
                }
            }
        });
    }

    @Override
    public void onReceive(Consumer<byte[]> handler) {
        this.receiveHandler = handler;
    }

    @Override
    public void close() {
        closed = true;
        if (peer.closed) {
            deliveryExecutor.shutdown();
        }
    }
}
