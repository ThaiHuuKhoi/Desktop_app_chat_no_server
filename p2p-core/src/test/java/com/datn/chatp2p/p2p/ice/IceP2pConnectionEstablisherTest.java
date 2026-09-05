package com.datn.chatp2p.p2p.ice;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.signal.ice.IceAnswerPayload;
import com.datn.chatp2p.common.signal.ice.IceOfferPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra toan bo luong ICE that giua 2 {@link IceP2pConnectionEstablisher}
 * tren CUNG MOT MAY (khac cong UDP, khong qua STUN/mang ngoai) - dung theo
 * goi y "test 2 instance tren cung may" o Tai-lieu-ky-thuat.md Phan F.5.2, de
 * xac nhan tich hop dung ma khong can 2 may vat ly / mang that.
 *
 * <p>Khong truyen STUN server nao (danh sach rong) - tren localhost chi can
 * host candidate la du de 2 Agent thay nhau va ket noi truc tiep, khong can
 * server-reflexive candidate.
 */
class IceP2pConnectionEstablisherTest {

    private IceP2pConnectionEstablisher offerer;
    private IceP2pConnectionEstablisher answerer;

    @AfterEach
    void tearDown() {
        if (offerer != null) offerer.dispose();
        if (answerer != null) answerer.dispose();
    }

    @Test
    void establishesDirectConnectionAndExchangesDataOnLocalhost() throws Exception {
        offerer = new IceP2pConnectionEstablisher(List.of());
        answerer = new IceP2pConnectionEstablisher(List.of());

        AtomicReference<DataChannel> offererChannel = new AtomicReference<>();
        AtomicReference<DataChannel> answererChannel = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch bothConnected = new CountDownLatch(2);

        offerer.onConnected(channel -> {
            offererChannel.set(channel);
            bothConnected.countDown();
        });
        offerer.onFailed(failure::set);

        answerer.onConnected(channel -> {
            answererChannel.set(channel);
            bothConnected.countDown();
        });
        answerer.onFailed(failure::set);

        // Trao doi offer/answer truc tiep trong test (thay cho SignalingClient
        // that - o day chi kiem tra rieng lop dieu phoi ICE, khong dung toi
        // WebSocketSignalingClient).
        IceOfferPayload offer = offerer.createOffer();
        IceAnswerPayload answer = answerer.createAnswer(offer);
        offerer.acceptAnswer(answer);

        boolean connectedInTime = bothConnected.await(15, TimeUnit.SECONDS);
        if (failure.get() != null) {
            throw new AssertionError("ICE bao loi truoc khi ket noi xong", failure.get());
        }
        assertTrue(connectedInTime, "Ca 2 ben phai bao COMPLETED trong 15s tren localhost");

        DataChannel fromOfferer = offererChannel.get();
        DataChannel fromAnswerer = answererChannel.get();
        assertNotNull(fromOfferer);
        assertNotNull(fromAnswerer);

        CountDownLatch answererReceived = new CountDownLatch(1);
        AtomicReference<String> receivedText = new AtomicReference<>();
        fromAnswerer.onReceive(data -> {
            receivedText.set(new String(data, StandardCharsets.UTF_8));
            answererReceived.countDown();
        });

        fromOfferer.send("xin chao qua ICE that".getBytes(StandardCharsets.UTF_8));

        assertTrue(answererReceived.await(5, TimeUnit.SECONDS), "Answerer phai nhan duoc du lieu qua kenh ICE that");
        assertEquals("xin chao qua ICE that", receivedText.get());

        // Do hieu nang (Tai-lieu-ky-thuat.md Phan F.1): tren localhost, khong cau
        // hinh TURN, ca 2 ben phai ket noi TRUC TIEP (khong qua relay) va thiet
        // lap rat nhanh (thuc te quan sat duoc ~1s, nhung khong ep con so cu the
        // vao test de tranh flaky tren may cham/CI qua tai).
        var offererStats = offerer.getStats();
        var answererStats = answerer.getStats();
        assertTrue(offererStats.isPresent(), "Phai co IceConnectionStats sau khi COMPLETED");
        assertTrue(answererStats.isPresent());
        assertFalse(offererStats.get().usingRelay(), "Localhost khong co TURN - khong duoc bao la dang dung relay");
        assertFalse(answererStats.get().usingRelay());
        assertTrue(offererStats.get().establishmentMillis() >= 0);
        assertTrue(answererStats.get().establishmentMillis() >= 0);

        fromOfferer.close();
        fromAnswerer.close();
    }
}
