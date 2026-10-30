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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link RoomSession#dispatchEnvelope} co lap loi dung nguyen tac
 * H.1 (Tai-lieu-ky-thuat.md) giua NHIEU handler dang ky cho CUNG 1
 * {@link EnvelopeType} - {@link RoomSession#onEnvelope} cho phep dang ky
 * nhieu lan cho cung 1 type (xem javadoc cua no, vi du 1 handler "ghi log"
 * va 1 handler rieng "cap nhat UI" cung nghe {@code EnvelopeType.MESSAGE}).
 * Neu handler DAU TIEN co bug va nem loi, cac handler dang ky SAU no cho
 * cung type khong duoc phep bi bo lo cho DUNG ban tin do.
 */
class RoomSessionEnvelopeDispatchErrorHandlingTest {

    @Test
    void oneThrowingEnvelopeHandlerDoesNotBlockOtherHandlersForTheSameType() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession sender = new RoomSession("room-dispatch", "sender", "Sender", new LoopbackSignalingClient(hub), List.of());
        RoomSession receiver = new RoomSession("room-dispatch", "receiver", "Receiver", new LoopbackSignalingClient(hub), List.of());

        CountDownLatch bothConnected = new CountDownLatch(2);
        sender.onPeerJoined(connection -> bothConnected.countDown());
        receiver.onPeerJoined(connection -> bothConnected.countDown());

        sender.join("ws://fake-signaling-server/ws");
        receiver.join("ws://fake-signaling-server/ws");
        assertTrue(bothConnected.await(15, TimeUnit.SECONDS), "Ca 2 ben phai ket noi xong truoc khi thu dispatch");

        // Dang ky 2 handler CHO CUNG 1 EnvelopeType.MESSAGE tren "receiver" - dung
        // dung kha nang da duoc onEnvelope() cong bo ("co the goi nhieu lan cho
        // cung 1 type"). Handler DAU TIEN co y NEM LOI (mo phong bug o phia ung
        // dung/UI) - handler THU HAI phai VAN duoc goi cho DUNG ban tin do.
        BlockingQueue<Envelope> secondHandlerInbox = new ArrayBlockingQueue<>(10);
        receiver.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> {
            throw new RuntimeException("gia lap bug o handler dau tien");
        });
        receiver.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> secondHandlerInbox.add(envelope));

        sender.broadcast(EnvelopeType.MESSAGE, new MessagePayload("m-1", "sender", "xin chao", 1L));

        Envelope received = secondHandlerInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Handler thu 2 (dang ky SAU handler nem loi, cho CUNG type) phai van duoc goi");

        sender.leave();
        receiver.leave();
    }
}
