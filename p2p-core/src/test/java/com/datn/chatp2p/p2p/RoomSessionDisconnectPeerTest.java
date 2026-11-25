package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.ice.IceP2pConnectionEstablisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoomSession#disconnectPeer}: tang tren ngat HAN 1 peer (khong co bang chung mat khau phong, chu ky danh
 * tinh sai). Truoc day peer do chi bi bo qua ban tin - duong UDP van mo, va ho gui lai OFFER la co duong moi.
 * Dung ICE that + ECDH that o phia "ke xau" nhu {@link RoomSessionEstablishedConnectionOverwriteSecurityTest}.
 */
class RoomSessionDisconnectPeerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void disconnectedPeerLosesItsConnectionPortAndCannotReconnectWithANewOffer() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();
        RoomSession victim = new RoomSession("room-kick", "victim", "Victim", new LoopbackSignalingClient(hub), List.of());
        victim.join("ws://fake-signaling-server/ws");

        LoopbackSignalingClient intruderClient = new LoopbackSignalingClient(hub);
        BlockingQueue<SignalMessage> intruderInbox = new LinkedBlockingQueue<>();
        intruderClient.onAnswer(intruderInbox::add);
        intruderClient.connect("ws://fake-signaling-server/ws", "room-kick", "intruder", "Intruder");

        BlockingQueue<PeerConnection> joined = new LinkedBlockingQueue<>();
        victim.onPeerJoined(joined::add);
        BlockingQueue<String> left = new LinkedBlockingQueue<>();
        victim.onPeerLeft(left::add);

        IceP2pConnectionEstablisher intruderIce = new IceP2pConnectionEstablisher(List.of());
        IceP2pConnectionEstablisher intruderIceRetry = new IceP2pConnectionEstablisher(List.of());
        PeerConnection intruderSide = null;
        try {
            IceOfferPayload offer = intruderIce.createOffer();
            intruderClient.sendOffer("victim", objectMapper.writeValueAsString(offer));
            SignalMessage answerMessage = intruderInbox.poll(10, TimeUnit.SECONDS);
            assertNotNull(answerMessage, "Victim phai tra loi OFFER dau tien");
            IceAnswerPayload answer = objectMapper.readValue(answerMessage.getPayload(), IceAnswerPayload.class);

            BlockingQueue<DataChannel> channels = new LinkedBlockingQueue<>();
            intruderIce.onConnected(channels::add);
            intruderIce.acceptAnswer(answer);
            DataChannel channel = channels.poll(10, TimeUnit.SECONDS);
            assertNotNull(channel, "ICE phai hoan tat that");
            intruderSide = new PeerConnection("victim", channel, KeyExchangeService.generateKeyPair(),
                    (from, envelope) -> { }, () -> { });
            intruderSide.sendEcdhPublicKey();
            assertNotNull(joined.poll(10, TimeUnit.SECONDS), "Victim phai ket noi xong voi intruder");
            int victimPort = parsePort(answer.candidates().get(0));

            victim.disconnectPeer("intruder");

            assertNull(victim.getPeerConnection("intruder"), "Ket noi phai bi dong");
            assertFalse(victim.getPeerIds().contains("intruder"));
            assertTrue(victim.isBlocked("intruder"));
            assertTrue(left.isEmpty(), "disconnectPeer khong phat onPeerLeft - tang tren tu don trang thai");
            try (DatagramSocket freed = new DatagramSocket(victimPort)) {
                assertTrue(freed.isBound(), "Cong UDP cua ket noi bi ngat phai duoc giai phong");
            }

            // Intruder gui lai OFFER de mo duong moi - phai bi bo qua, khong co ANSWER.
            intruderClient.sendOffer("victim", objectMapper.writeValueAsString(intruderIceRetry.createOffer()));
            assertNull(intruderInbox.poll(2, TimeUnit.SECONDS), "Peer da bi chan khong duoc mo lai duong bang OFFER moi");
            assertNull(victim.getPeerConnection("intruder"));
        } finally {
            if (intruderSide != null) {
                intruderSide.close();
            }
            intruderIce.dispose();
            intruderIceRetry.dispose();
            victim.leave();
            intruderClient.disconnect();
        }
    }

    @Test
    void blockedPeerIsSkippedWhenPeerListArrivesAgainAfterSignalingReconnect() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();
        LoopbackSignalingClient intruderClient = new LoopbackSignalingClient(hub);
        BlockingQueue<SignalMessage> offersToIntruder = new LinkedBlockingQueue<>();
        intruderClient.onOffer(offersToIntruder::add);
        intruderClient.connect("ws://fake-signaling-server/ws", "room-relist", "intruder", "Intruder");

        RoomSession victim = new RoomSession("room-relist", "victim", "Victim", new LoopbackSignalingClient(hub), List.of());
        try {
            victim.disconnectPeer("intruder");
            // JOIN -> PEER_LIST co "intruder": binh thuong victim se gui OFFER, nhung intruder da bi chan.
            victim.join("ws://fake-signaling-server/ws");

            assertNull(offersToIntruder.poll(2, TimeUnit.SECONDS), "Khong duoc chu dong gui OFFER toi peer da bi chan");
        } finally {
            victim.leave();
            intruderClient.disconnect();
        }
    }

    private static int parsePort(String candidateLine) {
        String body = candidateLine.startsWith("candidate:") ? candidateLine.substring("candidate:".length()) : candidateLine;
        return Integer.parseInt(body.trim().split("\\s+")[5]);
    }
}
