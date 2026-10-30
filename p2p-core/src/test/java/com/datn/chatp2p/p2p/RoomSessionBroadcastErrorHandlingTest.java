package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link RoomSession#broadcast} co lap loi dung nguyen tac H.1
 * (Tai-lieu-ky-thuat.md) da ap dung xuyen suot du an: 1 peer gui that bai
 * (channel cua no vua dong dung luc dang broadcast - vd race voi peer do
 * roi phong/mat mang) KHONG duoc chan viec gui toi cac peer con lai trong
 * cung vong lap.
 *
 * <p>Dung mesh 3 peer that (giong {@link RoomSessionThreePeerMeshTest}) roi
 * chu dong dong truoc kenh cua p1 toi p2 (qua
 * {@link RoomSession#getPeerConnection(String)}, chi danh cho test) de mo
 * phong dung tinh huong loi cuc bo ma khong can dan dung ca kich ban mang
 * that (peer roi phong/socket loi that su).
 */
class RoomSessionBroadcastErrorHandlingTest {

    @Test
    void oneFailedSendDuringBroadcastDoesNotBlockTheOtherPeers() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession p1 = new RoomSession("room-broadcast", "p1", "P1", new LoopbackSignalingClient(hub), List.of());
        RoomSession p2 = new RoomSession("room-broadcast", "p2", "P2", new LoopbackSignalingClient(hub), List.of());
        RoomSession p3 = new RoomSession("room-broadcast", "p3", "P3", new LoopbackSignalingClient(hub), List.of());

        CountDownLatch allConnected = new CountDownLatch(6);
        AtomicReference<Throwable> anyEstablishmentFailure = new AtomicReference<>();
        p1.onPeerJoined(connection -> allConnected.countDown());
        p2.onPeerJoined(connection -> allConnected.countDown());
        p3.onPeerJoined(connection -> allConnected.countDown());
        p1.onConnectionFailed((peerId, error) -> anyEstablishmentFailure.compareAndSet(null, error));
        p2.onConnectionFailed((peerId, error) -> anyEstablishmentFailure.compareAndSet(null, error));
        p3.onConnectionFailed((peerId, error) -> anyEstablishmentFailure.compareAndSet(null, error));

        p1.join("ws://fake-signaling-server/ws");
        p2.join("ws://fake-signaling-server/ws");
        p3.join("ws://fake-signaling-server/ws");

        assertTrue(allConnected.await(30, TimeUnit.SECONDS), "Ca 3 peer phai tao thanh mesh day du truoc khi thu broadcast");
        if (anyEstablishmentFailure.get() != null) {
            throw new AssertionError("Co loi thiet lap ket noi khi dung mesh", anyEstablishmentFailure.get());
        }

        // Dong truc tiep kenh cua p1 toi p2 - mo phong loi cuc bo (khong di qua
        // PEER_LEFT/leave() binh thuong) de kenh nay chac chan nem loi khi
        // p1.broadcast() sau do goi send() tren no.
        p1.getPeerConnection("p2").close();

        BlockingQueue<Envelope> p3Inbox = new ArrayBlockingQueue<>(10);
        p3.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> p3Inbox.add(envelope));

        assertDoesNotThrow(() ->
                        p1.broadcast(EnvelopeType.MESSAGE, new MessagePayload("m-broadcast-1", "p1", "chao ca phong", 1L)),
                "broadcast() khong duoc nem loi ra ngoai du 1 peer (p2) gui that bai");

        assertNotNull(p3Inbox.poll(5, TimeUnit.SECONDS),
                "P3 (khong lien quan gi toi loi cua p2) van phai nhan duoc broadcast tu p1");

        p1.leave();
        p2.leave();
        p3.leave();
    }
}
