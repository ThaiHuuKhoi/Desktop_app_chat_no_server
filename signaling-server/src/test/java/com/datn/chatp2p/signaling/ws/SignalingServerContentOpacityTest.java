package com.datn.chatp2p.signaling.ws;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan claim bao mat cot loi da neu trong Tai-lieu-ky-thuat.md Phan F.2:
 * "Signaling server khong bao gio doc/giai ma noi dung - chi relay SignalMessage
 * (da kiem chung bang code review: SignalingWebSocketHandler chi doc
 * type/roomId/toPeerId, khong dung vao payload)" - claim nay truoc gio moi
 * duoc xac nhan bang DOC CODE, chua co test THAT nao chung minh. Lop nay
 * viet test that cho dung 2 y trong claim:
 * <ol>
 *   <li>{@code payload} duoc relay NGUYEN VAN, ke ca khi khong phai JSON hop
 *       le hoac chua noi dung "kha nghi" - chung minh server khong bao gio
 *       thu parse/dien giai no (neu co thu parse, payload khong phai JSON se
 *       lam nem exception).</li>
 *   <li>Noi dung {@code payload} khong bao gio xuat hien trong log server
 *       (Tai-lieu-ky-thuat.md Phan H.2: "Khong log noi dung payload") - dung
 *       Logback {@link ListAppender} de bat truc tiep cac dong log thuc su
 *       ghi ra trong luc xu ly, khong doan mo.</li>
 * </ol>
 */
class SignalingServerContentOpacityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void relaysArbitraryPayloadVerbatimWithoutParsingItsContent() throws Exception {
        RoomRegistry roomRegistry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(roomRegistry, objectMapper);

        FakeWebSocketSession targetSession = new FakeWebSocketSession("target-session");
        roomRegistry.join(new PeerSession(targetSession, "room-1", "target-peer", "Target"));
        FakeWebSocketSession senderSession = new FakeWebSocketSession("sender-session");

        // Co y KHONG phai JSON hop le, lai con chua ky tu "nguy hiem" (the HTML,
        // dau ngoac chua dong) - server phai relay y nguyen ma khong duoc thu
        // hieu/parse no, vi payload chi la 1 truong String co hoi trong SignalMessage.
        String opaquePayload = "day khong phai JSON: { chua dong ngoac, the <script>alert(1)</script>, unicode 日本語";

        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("room-1");
        offer.setFromPeerId("sender-peer");
        offer.setToPeerId("target-peer");
        offer.setPayload(opaquePayload);

        assertDoesNotThrow(() ->
                        handler.handleTextMessage(senderSession, new TextMessage(objectMapper.writeValueAsString(offer))),
                "Server khong duoc nem loi du payload khong phai JSON hop le - dung la khong bao gio parse payload");

        assertEquals(1, targetSession.getSentMessages().size(), "Target phai nhan dung 1 ban tin da relay");
        WebSocketMessage<?> rawRelayed = targetSession.getSentMessages().get(0);
        SignalMessage relayed = objectMapper.readValue((String) rawRelayed.getPayload(), SignalMessage.class);

        assertEquals(SignalType.OFFER, relayed.getType());
        assertEquals("sender-peer", relayed.getFromPeerId());
        assertEquals(opaquePayload, relayed.getPayload(),
                "Payload phai duoc relay NGUYEN VAN, tung ky tu mot - server khong duoc sua/dien giai no");
    }

    @Test
    void neverLogsPayloadContentDuringRelay() throws Exception {
        RoomRegistry roomRegistry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(roomRegistry, objectMapper);

        FakeWebSocketSession targetSession = new FakeWebSocketSession("target-session");
        roomRegistry.join(new PeerSession(targetSession, "room-1", "target-peer", "Target"));
        FakeWebSocketSession senderSession = new FakeWebSocketSession("sender-session");

        String secretMarker = "NOI-DUNG-TUYET-MAT-KHONG-DUOC-XUAT-HIEN-TRONG-LOG-a1b2c3";

        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("room-1");
        offer.setFromPeerId("sender-peer");
        offer.setToPeerId("target-peer");
        offer.setPayload(secretMarker);

        // Bat truc tiep log THAT cua SignalingWebSocketHandler trong luc xu ly -
        // khong doan, xem dung co dong log nao chua secretMarker khong.
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(SignalingWebSocketHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        try {
            handler.handleTextMessage(senderSession, new TextMessage(toJson(offer)));
        } finally {
            logbackLogger.detachAppender(appender);
        }

        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(secretMarker),
                    "Dong log khong duoc chua noi dung payload: " + event.getFormattedMessage());
        }
    }

    @Test
    void relaysVeryLargePayloadWithoutTruncationOrError() throws Exception {
        RoomRegistry roomRegistry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(roomRegistry, objectMapper);

        FakeWebSocketSession targetSession = new FakeWebSocketSession("target-session");
        roomRegistry.join(new PeerSession(targetSession, "room-1", "target-peer", "Target"));
        FakeWebSocketSession senderSession = new FakeWebSocketSession("sender-session");

        // Mo phong 1 IceOfferPayload thuc su lon (nhieu candidate) - xac nhan
        // server khong co gioi han/cat bot ngam nao doi voi payload lon.
        String largePayload = "x".repeat(200_000);

        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId("room-1");
        offer.setFromPeerId("sender-peer");
        offer.setToPeerId("target-peer");
        offer.setPayload(largePayload);

        assertDoesNotThrow(() -> handler.handleTextMessage(senderSession, new TextMessage(toJson(offer))));

        assertEquals(1, targetSession.getSentMessages().size());
        SignalMessage relayed = objectMapper.readValue(
                (String) targetSession.getSentMessages().get(0).getPayload(), SignalMessage.class);
        assertEquals(largePayload.length(), relayed.getPayload().length(),
                "Payload lon khong duoc bi cat bot khi relay");
        assertTrue(relayed.getPayload().equals(largePayload));
    }

    private String toJson(SignalMessage message) throws Exception {
        return objectMapper.writeValueAsString(message);
    }
}
