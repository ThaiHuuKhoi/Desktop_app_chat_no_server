package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import com.datn.chatp2p.p2p.signaling.SignalingClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link RoomSession#handleOffer} khong crash khi nhan 1 OFFER co
 * dung 1 dong ICE candidate SAI DINH DANG (hong giua duong, hoac gia mao co
 * y) - Tang 1 (Signaling) da xac nhan server KHONG BAO GIO validate noi dung
 * {@code payload}, nen 1 dong candidate hong hoan toan co the toi day
 * nguyen ven tu 1 peer bug hoac co y, khong chi tu loi JSON/sendOffer nhu
 * {@link RoomSessionErrorHandlingTest} da kiem tra. Dung dung nguyen tac
 * chiu loi giong het test do: 1 peer loi khong duoc chan cac peer con lai
 * trong cung {@code PEER_LIST}.
 */
class RoomSessionMalformedIceCandidateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void malformedIceCandidateInOfferDoesNotCrashAndDoesNotBlockOtherPeers() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession existing1 = new RoomSession("room-y", "existing1", "E1", new LoopbackSignalingClient(hub), List.of());
        RoomSession existing2 = new RoomSession("room-y", "existing2", "E2", new LoopbackSignalingClient(hub), List.of());
        existing1.join("ws://fake-signaling-server/ws");
        existing2.join("ws://fake-signaling-server/ws");

        AtomicReference<Throwable> existing1Failure = new AtomicReference<>();
        CountDownLatch existing1FailedLatch = new CountDownLatch(1);
        existing1.onConnectionFailed((peerId, error) -> {
            if ("self".equals(peerId)) {
                existing1Failure.set(error);
                existing1FailedLatch.countDown();
            }
        });

        CountDownLatch existing2SeesSelf = new CountDownLatch(1);
        existing2.onPeerJoined(connection -> existing2SeesSelf.countDown());

        // "self" gui OFFER binh thuong toi CA HAI existing peer - nhung can thiep
        // NGAY TRUOC KHI toi Hub de lam hong 1 dong candidate CHI trong OFFER gui
        // toi existing1 (existing2 van nhan OFFER nguyen ven, hop le) - mo phong 1
        // dong candidate bi hong/gia mao giua duong ma KHONG lam sai dinh dang JSON
        // tong the (server van relay binh thuong, chi noi dung ben trong 1 phan tu
        // cua candidates[] la rac).
        SignalingClient realSelfClient = new LoopbackSignalingClient(hub);
        SignalingClient tamperingClient = new SignalingClient() {
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
                realSelfClient.sendOffer(toPeerId, "existing1".equals(toPeerId) ? corruptOneCandidate(sdp) : sdp);
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

        RoomSession self = new RoomSession("room-y", "self", "Self", tamperingClient, List.of());
        self.join("ws://fake-signaling-server/ws");

        assertTrue(existing1FailedLatch.await(5, TimeUnit.SECONDS),
                "existing1 phai bao onConnectionFailed cho self - khong duoc crash im lang hoac treo vo thoi han");
        assertNotNull(existing1Failure.get());

        // Diem mau chot: du OFFER toi existing1 bi hong, existing2 (nhan OFFER
        // nguyen ven) van phai ket noi duoc binh thuong voi self - dung nguyen tac
        // H.1 (loi cuc bo khong duoc lam gian doan xu ly cho cac doi tuong khac).
        assertTrue(existing2SeesSelf.await(15, TimeUnit.SECONDS),
                "existing2 phai van ket noi duoc voi self du OFFER toi existing1 bi hong");

        self.leave();
        existing1.leave();
        existing2.leave();
    }

    private String corruptOneCandidate(String offerJson) {
        try {
            IceOfferPayload offer = objectMapper.readValue(offerJson, IceOfferPayload.class);
            List<String> corrupted = new ArrayList<>(offer.candidates());
            if (!corrupted.isEmpty()) {
                corrupted.set(0, "day khong phai dong candidate hop le chut nao ca");
            }
            IceOfferPayload tampered = new IceOfferPayload(offer.ufrag(), offer.password(), corrupted);
            return objectMapper.writeValueAsString(tampered);
        } catch (Exception e) {
            throw new RuntimeException("Khong the gia mao offer JSON trong test: " + offerJson, e);
        }
    }
}
