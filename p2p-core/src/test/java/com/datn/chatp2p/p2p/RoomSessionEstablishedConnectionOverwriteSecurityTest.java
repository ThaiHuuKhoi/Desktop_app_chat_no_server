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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tang 5, bao mat: {@link RoomSession#createEstablisherFor} da duoc va truoc
 * day de dispose() gia tri CU trong {@code pendingEstablishers} khi bi GHI DE
 * (xem {@link RoomSessionDuplicateOfferSecurityTest}) - nhung ban va do CHUA
 * BAO GIO duoc ap dung cho {@code peers} trong {@code onIceConnected()}. Neu
 * 1 phien ICE THU HAI hoan tat THAT SU duoi CUNG 1 {@code peerId} da co san
 * 1 {@link PeerConnection} DANG HOAT DONG trong {@code peers}, gia tri cu bi
 * GHI DE ma khong dong - "chiem quyen ket noi" (connection hijack): moi
 * {@code sendTo}/{@code broadcast} sau do se bi chuyen huong sang ket noi
 * MOI ma khong ai biet, con ket noi CU bi "mo coi" (ro ri socket/thread vinh
 * vien).
 *
 * <p>Kich ban thuc te co the trigger: 1 peer gui lai OFFER (do bug ket noi
 * lai khong LEAVE truoc, hoac co y gia mao) duoi peerId da dang duoc dung -
 * Tai-lieu-ky-thuat.md Phan H.2 da xac nhan signaling KHONG xac thuc/gioi
 * han ai duoc dung peerId nao.
 *
 * <p>Dung ky thuat giong het {@link RoomSessionDuplicateOfferSecurityTest}
 * (tu dieu khien {@link IceP2pConnectionEstablisher} thay vi qua RoomSession
 * day du o phia "attacker") nhung lan nay CHO CA 2 lan handshake ICE hoan
 * tat THAT SU, VA phia attacker cung tu tao {@link PeerConnection} that cua
 * rieng no de HOAN TAT CA ECDH (khong chi ICE) - neu khong, PeerConnection
 * cua victim se cho public key ECDH mai mai (khong ai tra loi), onPeerJoined
 * khong bao gio ban ra, khong the quan sat duoc dung diem victim's peers map
 * co bi ghi de hay khong.
 */
class RoomSessionEstablishedConnectionOverwriteSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aSecondFullyEstablishedConnectionUnderTheSamePeerIdDoesNotLeakTheFirstOne() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession victim = new RoomSession("room-hijack", "victim", "Victim", new LoopbackSignalingClient(hub), List.of());
        victim.join("ws://fake-signaling-server/ws");

        LoopbackSignalingClient attackerClient = new LoopbackSignalingClient(hub);
        BlockingQueue<SignalMessage> attackerInbox = new LinkedBlockingQueue<>();
        attackerClient.onAnswer(attackerInbox::add);
        attackerClient.connect("ws://fake-signaling-server/ws", "room-hijack", "attacker", "Attacker");

        BlockingQueue<PeerConnection> victimJoinedAttacker = new LinkedBlockingQueue<>();
        victim.onPeerJoined(connection -> {
            if ("attacker".equals(connection.getPeerId())) {
                victimJoinedAttacker.add(connection);
            }
        });

        IceP2pConnectionEstablisher attackerEstablisher1 = new IceP2pConnectionEstablisher(List.of());
        IceP2pConnectionEstablisher attackerEstablisher2 = new IceP2pConnectionEstablisher(List.of());
        PeerConnection attackerSide1 = null;
        PeerConnection attackerSide2 = null;
        try {
            // --- Lan 1: ICE that + ECDH that hoan tat binh thuong (peer hop le ket noi that su) ---
            IceOfferPayload offer1 = attackerEstablisher1.createOffer();
            attackerClient.sendOffer("victim", objectMapper.writeValueAsString(offer1));
            SignalMessage answerMsg1 = attackerInbox.poll(10, TimeUnit.SECONDS);
            assertNotNull(answerMsg1, "Victim phai tra loi ANSWER cho OFFER dau tien");
            IceAnswerPayload answer1 = objectMapper.readValue(answerMsg1.getPayload(), IceAnswerPayload.class);

            BlockingQueue<DataChannel> attackerChannels1 = new LinkedBlockingQueue<>();
            attackerEstablisher1.onConnected(attackerChannels1::add);
            attackerEstablisher1.acceptAnswer(answer1);
            DataChannel attackerChannel1 = attackerChannels1.poll(10, TimeUnit.SECONDS);
            assertNotNull(attackerChannel1, "ICE lan 1 (attacker) phai hoan tat that");

            // Phia attacker cung phai tu tao PeerConnection THAT va goi sendEcdhPublicKey()
            // de HOAN TAT ca buoc trao khoa ECDH - neu khong, PeerConnection cua victim se
            // cho public key ECDH mai mai, onPeerJoined khong bao gio ban ra.
            attackerSide1 = new PeerConnection("victim", attackerChannel1, KeyExchangeService.generateKeyPair(),
                    (from, envelope) -> { }, () -> { });
            attackerSide1.sendEcdhPublicKey();

            PeerConnection firstConnection = victimJoinedAttacker.poll(10, TimeUnit.SECONDS);
            assertNotNull(firstConnection, "Victim phai thay 'attacker' ket noi xong lan dau (peers da co entry that)");

            int victimPortForFirstConnection = parsePort(answer1.candidates().get(0));

            // --- Lan 2: CUNG peerId "attacker" hoan tat THEM 1 phien ICE+ECDH that KHAC ---
            // (mo phong gui lai OFFER duoi peerId da dung, xem javadoc lop nay) - victim
            // se tao establisher/PeerConnection MOI, dung peerId "attacker" y het lan 1.
            IceOfferPayload offer2 = attackerEstablisher2.createOffer();
            attackerClient.sendOffer("victim", objectMapper.writeValueAsString(offer2));
            SignalMessage answerMsg2 = attackerInbox.poll(10, TimeUnit.SECONDS);
            assertNotNull(answerMsg2, "Victim phai tra loi ANSWER cho OFFER thu 2 (cung peerId)");
            IceAnswerPayload answer2 = objectMapper.readValue(answerMsg2.getPayload(), IceAnswerPayload.class);

            BlockingQueue<DataChannel> attackerChannels2 = new LinkedBlockingQueue<>();
            attackerEstablisher2.onConnected(attackerChannels2::add);
            attackerEstablisher2.acceptAnswer(answer2);
            DataChannel attackerChannel2 = attackerChannels2.poll(10, TimeUnit.SECONDS);
            assertNotNull(attackerChannel2, "ICE lan 2 (cung peerId) phai hoan tat that");

            attackerSide2 = new PeerConnection("victim", attackerChannel2, KeyExchangeService.generateKeyPair(),
                    (from, envelope) -> { }, () -> { });
            attackerSide2.sendEcdhPublicKey();

            PeerConnection secondConnection = victimJoinedAttacker.poll(10, TimeUnit.SECONDS);
            assertNotNull(secondConnection, "Victim phai bao onPeerJoined LAN NUA cho peerId 'attacker' (peers bi ghi de that)");
            assertNotSame(firstConnection, secondConnection,
                    "2 lan hoan tat ICE+ECDH duoi cung peerId phai tao ra 2 PeerConnection KHAC nhau trong peers - "
                            + "chung minh peers.put() da GHI DE, dung diem mau chot cua lo hong nay");

            // DIEM MAU CHOT: PeerConnection DAU TIEN (da bi ghi de, khong con trong peers
            // nua) phai duoc dong dung luc bi ghi de - xac nhan bang cach bind lai duoc
            // CHINH XAC cong UDP cua no bang 1 DatagramSocket moi (neu con "mo coi", bind
            // se nem BindException).
            try (DatagramSocket verifyFreed = new DatagramSocket(victimPortForFirstConnection)) {
                assertTrue(verifyFreed.isBound(),
                        "Cong UDP cua PeerConnection DAU TIEN phai duoc giai phong khi bi ghi de trong peers - "
                                + "khong duoc de 'mo coi' vinh vien du no da bi thay the boi 1 ket noi moi cung peerId");
            }
        } finally {
            if (attackerSide1 != null) {
                attackerSide1.close();
            }
            if (attackerSide2 != null) {
                attackerSide2.close();
            }
            attackerEstablisher1.dispose();
            attackerEstablisher2.dispose();
            victim.leave();
            attackerClient.disconnect();
        }
    }

    private static int parsePort(String candidateLine) {
        String body = candidateLine.startsWith("candidate:") ? candidateLine.substring("candidate:".length()) : candidateLine;
        String[] tokens = body.trim().split("\\s+");
        return Integer.parseInt(tokens[5]);
    }
}
