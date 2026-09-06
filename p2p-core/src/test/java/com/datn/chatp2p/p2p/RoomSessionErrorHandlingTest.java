package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.p2p.signaling.SignalingClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link RoomSession} co lap loi dung tinh chat cua "Tang 1 -
 * Signaling" (Tai-lieu-ky-thuat.md Phan H.1): 1 peer that bai khi thiet lap
 * ket noi (o day gia lap gui OFFER loi) KHONG duoc chan viec ket noi toi cac
 * peer con lai trong CUNG 1 {@code PEER_LIST} - dung nguyen tac da ap dung o
 * {@code SignalingWebSocketHandlerErrorHandlingTest} phia server.
 */
class RoomSessionErrorHandlingTest {

    @Test
    void oneFailedPeerInPeerListDoesNotBlockConnectingToOthers() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        // existing1/existing2 da co san trong phong TRUOC khi "self" vao -> self se
        // thay ca 2 qua PEER_LIST va phai chu dong gui OFFER toi ca hai.
        RoomSession existing1 = new RoomSession("room-x", "existing1", "E1", new LoopbackSignalingClient(hub), List.of());
        RoomSession existing2 = new RoomSession("room-x", "existing2", "E2", new LoopbackSignalingClient(hub), List.of());
        existing1.join("ws://fake-signaling-server/ws");
        existing2.join("ws://fake-signaling-server/ws");

        // SignalingClient rieng cho "self": gui OFFER toi "existing1" gia lap LOI,
        // gui OFFER toi "existing2" van di qua binh thuong.
        SignalingClient realSelfClient = new LoopbackSignalingClient(hub);
        SignalingClient failingForExisting1 = new SignalingClient() {
            @Override
            public void connect(String serverUri, String roomId, String peerId, String userName) {
                realSelfClient.connect(serverUri, roomId, peerId, userName);
            }

            @Override
            public void onPeerJoined(Consumer<SignalMessage> handler) {
                realSelfClient.onPeerJoined(handler);
            }

            @Override
            public void onPeerLeft(Consumer<SignalMessage> handler) {
                realSelfClient.onPeerLeft(handler);
            }

            @Override
            public void onPeerList(Consumer<SignalMessage> handler) {
                realSelfClient.onPeerList(handler);
            }

            @Override
            public void onOffer(Consumer<SignalMessage> handler) {
                realSelfClient.onOffer(handler);
            }

            @Override
            public void onAnswer(Consumer<SignalMessage> handler) {
                realSelfClient.onAnswer(handler);
            }

            @Override
            public void onIceCandidate(Consumer<SignalMessage> handler) {
                realSelfClient.onIceCandidate(handler);
            }

            @Override
            public void sendOffer(String toPeerId, String sdp) {
                if ("existing1".equals(toPeerId)) {
                    throw new RuntimeException("gia lap gui OFFER toi existing1 that bai");
                }
                realSelfClient.sendOffer(toPeerId, sdp);
            }

            @Override
            public void sendAnswer(String toPeerId, String sdp) {
                realSelfClient.sendAnswer(toPeerId, sdp);
            }

            @Override
            public void sendIceCandidate(String toPeerId, String candidate) {
                realSelfClient.sendIceCandidate(toPeerId, candidate);
            }

            @Override
            public void disconnect() {
                realSelfClient.disconnect();
            }
        };

        RoomSession self = new RoomSession("room-x", "self", "Self", failingForExisting1, List.of());

        AtomicReference<Throwable> failureForExisting1 = new AtomicReference<>();
        CountDownLatch failureLatch = new CountDownLatch(1);
        self.onConnectionFailed((peerId, error) -> {
            if ("existing1".equals(peerId)) {
                failureForExisting1.set(error);
                failureLatch.countDown();
            }
        });

        CountDownLatch existing2SeesSelf = new CountDownLatch(1);
        existing2.onPeerJoined(connection -> existing2SeesSelf.countDown());

        self.join("ws://fake-signaling-server/ws");

        assertTrue(failureLatch.await(5, TimeUnit.SECONDS), "Phai bao onConnectionFailed cho existing1");
        assertNotNull(failureForExisting1.get());

        // connectAsOfferer da tao xong 1 IceP2pConnectionEstablisher (chiem 1 UDP socket)
        // cho existing1 TRUOC KHI sendOffer nem loi - neu RoomSession chi bao loi ma khong
        // don dep, establisher do "mo coi" vinh vien trong pendingEstablishers, ro ri socket.
        // Chi kiem tra DUNG peerId "existing1" (khong phai toan bo map) - vi establisher
        // that cho "existing2" van co the con dang cho ICE that hoan tat, khong phai ro ri.
        assertFalse(self.hasPendingEstablisherFor("existing1"),
                "Establisher that bai (existing1) khong duoc de mo coi trong pendingEstablishers - phai duoc dispose() va go ngay");

        // Diem mau chot: du existing1 that bai, self van phai ket noi duoc voi
        // existing2 binh thuong (khong bi chan boi loi cua existing1).
        assertTrue(existing2SeesSelf.await(15, TimeUnit.SECONDS),
                "existing2 phai van ket noi duoc voi self du existing1 that bai truoc do");

        self.leave();
        existing1.leave();
        existing2.leave();
    }
}
