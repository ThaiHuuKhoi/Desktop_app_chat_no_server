package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tang 5, chiu loi/kha nang mo rong: xac nhan {@link RoomSession#leave()}
 * GIAI PHONG THAT SU cong UDP cua ICE cho MOI peer da ket noi xong - khong
 * chi cho kich ban "bi ghi de" da phat hien o {@link RoomSessionEstablishedConnectionOverwriteSecurityTest}.
 *
 * <p>Nguyen nhan goc phat hien khi sua bug do: {@code onIceConnected()} truoc
 * day KHONG BAO GIO giu lai tham chieu {@code IceP2pConnectionEstablisher}
 * (va {@code Agent} ice4j ben trong no) sau khi ICE thanh cong - chi xoa no
 * khoi {@code pendingEstablishers} roi mat tham chieu vinh vien.
 * {@code PeerConnection.close()} (goi tu {@link RoomSession#leave()}) chi
 * dong duoc 1 THAM CHIEU {@code DatagramSocket} lay tu {@code component.getSocket()} -
 * KHONG du de giai phong that su cong UDP o muc OS. Nghia la: TRUOC KHI sua,
 * MOI phong tung ket noi thanh cong voi it nhat 1 peer se ro ri cong UDP cua
 * peer do VINH VIEN, ke ca khi nguoi dung goi leave() dung cach - khong can
 * bat ky kich ban tan cong/bat thuong nao ca.
 *
 * <p>Da sua bang cach them {@code RoomSession.connectedEstablishers} (giu lai
 * establisher cho tung peer DA ket noi xong) va dispose() no trong CA leave()
 * lan handlePeerLeftNotice, khong chi trong nhanh ghi de cua onIceConnected().
 */
class RoomSessionLeaveReleasesEstablishedIcePortsTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Test
    void leaveAfterANormalSuccessfulConnectionReleasesTheIcePortForRebinding() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession self = new RoomSession("room-normal-leave", "self", "Self", new LoopbackSignalingClient(hub), List.of());
        self.join("ws://fake-signaling-server/ws");

        LoopbackSignalingClient peerClient = new LoopbackSignalingClient(hub);
        BlockingQueue<SignalMessage> peerInbox = new LinkedBlockingQueue<>();
        peerClient.onAnswer(peerInbox::add);
        peerClient.connect("ws://fake-signaling-server/ws", "room-normal-leave", "peer1", "Peer1");

        BlockingQueue<PeerConnection> selfJoinedPeer = new LinkedBlockingQueue<>();
        self.onPeerJoined(connection -> {
            if ("peer1".equals(connection.getPeerId())) {
                selfJoinedPeer.add(connection);
            }
        });

        var peerEstablisher = new com.datn.chatp2p.p2p.ice.IceP2pConnectionEstablisher(List.of());
        PeerConnection peerSide = null;
        try {
            IceOfferPayload offer = peerEstablisher.createOffer();
            peerClient.sendOffer("self", objectMapper.writeValueAsString(offer));
            SignalMessage answerMsg = peerInbox.poll(10, TimeUnit.SECONDS);
            assertNotNull(answerMsg, "Self phai tra loi ANSWER cho OFFER binh thuong nay");
            IceAnswerPayload answer = objectMapper.readValue(answerMsg.getPayload(), IceAnswerPayload.class);

            BlockingQueue<com.datn.chatp2p.common.channel.DataChannel> peerChannels = new LinkedBlockingQueue<>();
            peerEstablisher.onConnected(peerChannels::add);
            peerEstablisher.acceptAnswer(answer);
            var peerChannel = peerChannels.poll(10, TimeUnit.SECONDS);
            assertNotNull(peerChannel, "ICE phai hoan tat that");

            peerSide = new PeerConnection("self", peerChannel,
                    com.datn.chatp2p.crypto.KeyExchangeService.generateKeyPair(), (from, envelope) -> { }, () -> { });
            peerSide.sendEcdhPublicKey();

            PeerConnection selfSide = selfJoinedPeer.poll(10, TimeUnit.SECONDS);
            assertNotNull(selfSide, "Self phai ket noi xong voi peer1 (kich ban BINH THUONG, khong tan cong)");

            int selfPortForPeer1 = parsePort(answer.candidates().get(0));

            // Roi phong DUNG CACH, khong co gi bat thuong/tan cong ca.
            self.leave();

            // DIEM MAU CHOT: SAU KHI leave() dung cach, cong UDP cua peer1 (da ket
            // noi thanh cong truoc do) phai duoc giai phong THAT SU - neu con "mo
            // coi" (BindException), nghia la MOI phong chat tung dung xong deu am
            // tham ro ri cong UDP vinh vien, du nguoi dung khong lam gi sai.
            try (DatagramSocket verifyFreed = new DatagramSocket(selfPortForPeer1)) {
                assertTrue(verifyFreed.isBound(),
                        "Cong UDP cua peer1 phai duoc giai phong sau leave() BINH THUONG - "
                                + "khong duoc ro ri chi vi ket noi da tung thanh cong");
            }
        } finally {
            if (peerSide != null) {
                peerSide.close();
            }
            peerEstablisher.dispose();
            peerClient.disconnect();
        }
    }

    private static int parsePort(String candidateLine) {
        String body = candidateLine.startsWith("candidate:") ? candidateLine.substring("candidate:".length()) : candidateLine;
        String[] tokens = body.trim().split("\\s+");
        return Integer.parseInt(tokens[5]);
    }
}
