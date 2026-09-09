package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import com.datn.chatp2p.p2p.ice.IceP2pConnectionEstablisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link RoomSession} khong the bi khai thac de gay DoS (can kiet
 * dai cong UDP cua ICE) chi bang cach gui NHIEU LAN OFFER toi CUNG 1 peer -
 * Tai-lieu-ky-thuat.md Phan H.2 da xac nhan signaling server KHONG gioi han
 * toc do/xac thuc ban tin, nen khong co gi ngan 1 peer (bug hoac co y tan
 * cong) gui lai OFFER lien tuc toi cung 1 nan nhan TRUOC KHI lan dau kip
 * hoan tat ICE.
 *
 * <p>Neu {@code RoomSession.createEstablisherFor} chi ghi de
 * {@code pendingEstablishers} ma khong dispose() gia tri cu, moi lan OFFER
 * lap lai se lam "mo coi" 1 {@code IceP2pConnectionEstablisher} (chiem 1 UDP
 * socket) - ke tan cong chi can gui du nhieu OFFER (khong can thanh cong
 * ket noi that su) de can kiet toan bo dai cong UDP huu han cua nan nhan,
 * lam nan nhan khong con ket noi P2P moi nao duoc nua.
 */
class RoomSessionDuplicateOfferSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendingTheSameOfferTwiceBeforeTheFirstCompletesDoesNotLeakTheDiscardedEstablisher() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        // "attacker" ket noi thang qua signaling (khong dung RoomSession day du) -
        // de tu do gui NHIEU OFFER lien tiep toi "victim", mo phong dung kich ban:
        // 1 peer gui lai OFFER nhieu lan truoc khi nan nhan kip xu ly xong lan dau.
        LoopbackSignalingClient attackerClient = new LoopbackSignalingClient(hub);
        BlockingQueue<SignalMessage> attackerInbox = new ArrayBlockingQueue<>(10);
        attackerClient.onAnswer(attackerInbox::add);
        attackerClient.connect("ws://fake-signaling-server/ws", "room-z", "attacker", "Attacker");

        RoomSession victim = new RoomSession("room-z", "victim", "Victim", new LoopbackSignalingClient(hub), List.of());
        victim.join("ws://fake-signaling-server/ws");

        // Tao 2 IceOfferPayload HOP LE, KHAC nhau (dung 2 IceP2pConnectionEstablisher
        // rieng chi de sinh offer dung dinh dang thuc su - khong can hoan tat ICE
        // that voi chung) roi gui lien tiep, KHONG doi answer 1 truoc khi gui offer 2 -
        // dung mo phong "OFFER lap lai truoc khi lan dau kip xong".
        IceP2pConnectionEstablisher decoyOfferer1 = new IceP2pConnectionEstablisher(List.of());
        IceP2pConnectionEstablisher decoyOfferer2 = new IceP2pConnectionEstablisher(List.of());
        try {
            IceOfferPayload offer1 = decoyOfferer1.createOffer();
            IceOfferPayload offer2 = decoyOfferer2.createOffer();

            attackerClient.sendOffer("victim", objectMapper.writeValueAsString(offer1));
            attackerClient.sendOffer("victim", objectMapper.writeValueAsString(offer2));

            SignalMessage answerMsg1 = attackerInbox.poll(10, TimeUnit.SECONDS);
            SignalMessage answerMsg2 = attackerInbox.poll(10, TimeUnit.SECONDS);
            assertNotNull(answerMsg1, "Victim phai tra loi ANSWER cho OFFER dau tien");
            assertNotNull(answerMsg2, "Victim phai tra loi ANSWER cho OFFER thu 2 (ghi de establisher dau tien)");

            IceAnswerPayload answer1 = objectMapper.readValue(answerMsg1.getPayload(), IceAnswerPayload.class);
            int victimPortForFirstOffer = parsePort(answer1.candidates().get(0));

            // Establisher DAU TIEN cua victim (danh cho OFFER 1, da bi ghi de boi
            // OFFER 2 trong pendingEstablishers) phai da duoc dispose() dung luc
            // OFFER 2 toi - xac nhan bang cach bind lai duoc CHINH XAC cong do
            // bang 1 DatagramSocket moi (neu con "mo coi", bind se nem BindException).
            try (DatagramSocket verifyFreed = new DatagramSocket(victimPortForFirstOffer)) {
                assertTrue(verifyFreed.isBound(),
                        "Cong UDP cua establisher dau tien phai duoc giai phong - khong duoc de 'mo coi' khi bi OFFER thu 2 ghi de");
            }
        } finally {
            decoyOfferer1.dispose();
            decoyOfferer2.dispose();
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
