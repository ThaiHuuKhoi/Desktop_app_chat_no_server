package com.datn.chatp2p.p2p.ice;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan viec khai bao them TURN server ({@link TurnServerConfig}) khong
 * lam hong luong ICE binh thuong - dung ky thuat giong het
 * {@link IceP2pConnectionEstablisherTest#establishesDirectConnectionAndExchangesDataOnLocalhost}
 * (2 Agent that tren localhost) nhung LAN NAY co khai bao them 1 TURN server
 * KHONG THE TOI DUOC (dia chi 192.0.2.1 - "TEST-NET-1" theo RFC 5737, danh
 * rieng cho tai lieu/test, dam bao KHONG bao gio duoc dinh tuyen tren
 * Internet that - chon dia chi nay thay vi 1 hostname that de test luon xac
 * dinh, khong phu thuoc tinh trang 1 server ben thu 3 nao do con song hay
 * khong).
 *
 * <p>Muc dich: TURN chi la 1 CandidateHarvester THEM VAO, khong duoc thay the
 * hay chan cac harvester khac - tren localhost, 2 Agent van phai ket noi
 * duoc TRUC TIEP qua host candidate (khong can, khong dung TURN) du co khai
 * bao 1 TURN server khong lien lac duoc. Day la kiem tra CHIU LOI/AN TOAN
 * cua viec wiring TURN, KHONG phai kiem tra TURN THAT SU relay du lieu (can
 * 1 TURN server that dang chay, ngoai pham vi moi truong dev hien tai - xem
 * docs/Bao-cao-thuc-hien-Nhiem-vu-A.md muc "Chua lam").
 */
class IceP2pConnectionEstablisherTurnFallbackTest {

    /** RFC 5737 TEST-NET-1 - danh rieng cho tai lieu/test, dam bao khong bao gio duoc dinh tuyen that. */
    private static final TransportAddress UNREACHABLE_TURN_SERVER =
            new TransportAddress("192.0.2.1", 3478, Transport.UDP);

    private IceP2pConnectionEstablisher offerer;
    private IceP2pConnectionEstablisher answerer;

    @AfterEach
    void tearDown() {
        if (offerer != null) offerer.dispose();
        if (answerer != null) answerer.dispose();
    }

    @Test
    void anUnreachableTurnServerDoesNotPreventIceFromConnectingViaHostCandidatesOnLocalhost() throws Exception {
        List<TurnServerConfig> turnServers = List.of(
                new TurnServerConfig(UNREACHABLE_TURN_SERVER, "khong-quan-trong", "khong-quan-trong"));

        offerer = new IceP2pConnectionEstablisher(List.of(), turnServers);
        answerer = new IceP2pConnectionEstablisher(List.of(), turnServers);

        AtomicReference<DataChannel> offererChannel = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch bothConnected = new CountDownLatch(2);

        offerer.onConnected(channel -> {
            offererChannel.set(channel);
            bothConnected.countDown();
        });
        offerer.onFailed(failure::set);
        answerer.onConnected(channel -> bothConnected.countDown());
        answerer.onFailed(failure::set);

        IceOfferPayload offer = offerer.createOffer();
        IceAnswerPayload answer = answerer.createAnswer(offer);
        offerer.acceptAnswer(answer);

        // Timeout rong hon test ICE thuong (15s) vi harvester TURN se thu lien lac
        // dia chi khong toi duoc truoc khi bo cuoc VOI RIENG harvester do (co the
        // ton vai giay do STUN/TURN tu retransmit theo RFC 5389) - ICE van phai
        // hoan tat qua host candidate trong luc do, khong bi TURN "khoa" lai.
        boolean connectedInTime = bothConnected.await(25, TimeUnit.SECONDS);
        if (failure.get() != null) {
            throw new AssertionError("ICE bao loi du van con host candidate dung duoc - "
                    + "TURN khong toi duoc khong duoc phep lam sap toan bo ICE", failure.get());
        }
        assertTrue(connectedInTime, "Ca 2 ben van phai ket noi xong qua host candidate "
                + "du co 1 TURN server khong toi duoc trong danh sach");
        assertNotNull(offererChannel.get());

        assertTrue(offerer.getStats().isPresent());
        assertFalse(offerer.getStats().get().usingRelay(),
                "TURN khong toi duoc khong bao gio duoc chon lam candidate pair - "
                        + "ket noi phai la TRUC TIEP qua host candidate tren localhost");
    }
}
