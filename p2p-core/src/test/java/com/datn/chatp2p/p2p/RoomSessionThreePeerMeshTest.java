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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra "kha nang mo rong" cua {@link RoomSession} vuot qua kich ban 2
 * peer da test truoc do (Tai-lieu-ky-thuat.md Phan B.3/F.3: kien truc mesh
 * day du, N peer -> moi peer giu N-1 ket noi song song) - dung 3 peer, lan
 * luot vao phong theo thu tu (P1 -> P2 -> P3), xac nhan CA 3 CAP deu tu ket
 * noi duoc voi nhau (khong chi P3 voi tung nguoi rieng le) va broadcast tu
 * 1 nguoi toi duoc CA 2 nguoi con lai.
 *
 * <p>Day cung la phep thu lai chinh xac fix o {@link RoomSession#handlePeerList}
 * (Tai-lieu-ky-thuat.md Phan H.1): khi P3 vao sau cung, PEER_LIST cua P3 co 2
 * phan tu (P1 va P2) - P3 phai lan luot chu dong ket noi voi CA HAI, khong
 * duoc dung lai sau phan tu dau tien.
 */
class RoomSessionThreePeerMeshTest {

    @Test
    void threePeersFormAFullMeshAndBroadcastReachesEveryone() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession p1 = new RoomSession("room-mesh", "p1", "P1", new LoopbackSignalingClient(hub), List.of());
        RoomSession p2 = new RoomSession("room-mesh", "p2", "P2", new LoopbackSignalingClient(hub), List.of());
        RoomSession p3 = new RoomSession("room-mesh", "p3", "P3", new LoopbackSignalingClient(hub), List.of());

        // Mesh day du 3 peer = 3 cap ket noi (p1-p2, p1-p3, p2-p3), moi cap bao
        // onPeerJoined 2 lan (1 lan tren moi RoomSession cua cap do) -> tong 6 lan.
        CountDownLatch allConnected = new CountDownLatch(6);
        AtomicReference<Throwable> anyFailure = new AtomicReference<>();

        p1.onPeerJoined(connection -> allConnected.countDown());
        p2.onPeerJoined(connection -> allConnected.countDown());
        p3.onPeerJoined(connection -> allConnected.countDown());
        p1.onConnectionFailed((peerId, error) -> anyFailure.compareAndSet(null, error));
        p2.onConnectionFailed((peerId, error) -> anyFailure.compareAndSet(null, error));
        p3.onConnectionFailed((peerId, error) -> anyFailure.compareAndSet(null, error));

        p1.join("ws://fake-signaling-server/ws");
        p2.join("ws://fake-signaling-server/ws");
        // P3 vao sau cung -> thay CA p1 lan p2 qua PEER_LIST, phai chu dong ket
        // noi voi CA HAI (dung phep thu cho vong lap trong handlePeerList).
        p3.join("ws://fake-signaling-server/ws");

        boolean connectedInTime = allConnected.await(30, TimeUnit.SECONDS);
        if (anyFailure.get() != null) {
            throw new AssertionError("Co loi ket noi trong luc dung mesh 3 peer", anyFailure.get());
        }
        assertTrue(connectedInTime, "Ca 3 peer phai tao thanh mesh day du (moi peer 2 ket noi) trong 30s");

        assertEquals(2, p1.getPeerIds().size(), "P1 phai ket noi voi ca P2 lan P3");
        assertEquals(2, p2.getPeerIds().size(), "P2 phai ket noi voi ca P1 lan P3");
        assertEquals(2, p3.getPeerIds().size(), "P3 phai ket noi voi ca P1 lan P2");
        assertTrue(p1.getPeerIds().containsAll(List.of("p2", "p3")));
        assertTrue(p2.getPeerIds().containsAll(List.of("p1", "p3")));
        assertTrue(p3.getPeerIds().containsAll(List.of("p1", "p2")));

        // Broadcast tu P1 phai toi CA P2 lan P3 - moi nguoi nhan truc tiep qua
        // PeerConnection rieng cua minh voi P1 (dung mesh, khong qua trung gian).
        BlockingQueue<Envelope> p2Inbox = new ArrayBlockingQueue<>(10);
        BlockingQueue<Envelope> p3Inbox = new ArrayBlockingQueue<>(10);
        p2.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> p2Inbox.add(envelope));
        p3.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> p3Inbox.add(envelope));

        p1.broadcast(EnvelopeType.MESSAGE, new MessagePayload("m-mesh-1", "p1", "chao ca phong", 1L));

        assertNotNull(p2Inbox.poll(5, TimeUnit.SECONDS), "P2 phai nhan duoc broadcast tu P1");
        assertNotNull(p3Inbox.poll(5, TimeUnit.SECONDS), "P3 phai nhan duoc broadcast tu P1");

        p1.leave();
        p2.leave();
        p3.leave();
    }
}
