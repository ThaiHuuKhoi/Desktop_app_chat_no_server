package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra rieng kha nang chiu loi cua {@link SignalingWebSocketHandler}
 * (Tai-lieu-ky-thuat.md Phan H.1) - dung {@link RoomRegistry} THAT (khong
 * mock, vi day la code don gian da co san, khong can gia lap) va
 * {@link FakeWebSocketSession} tu viet tay (KHONG dung Mockito - da xac nhan
 * that Mockito's inline mock maker (Byte Buddy) chua ho tro JDK 26 dang dung
 * trong moi truong nay: "Java 26 (70) is not supported by the current version
 * of Byte Buddy which officially supports Java 23 (67)").
 *
 * <p>Khac voi {@link SignalingWebSocketHandlerTest} dung WebSocket THAT qua
 * mang cho kich ban binh thuong - lop nay chi tap trung vao cac tinh huong
 * LOI kho tao ra bang WebSocket that (session dang dong dot ngot giua chung).
 */
class SignalingWebSocketHandlerErrorHandlingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void malformedJsonIsIgnoredWithoutThrowingOrClosingTheSession() {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession session = new FakeWebSocketSession("s1");

        // Truoc khi sua: doan nay nem JsonProcessingException ra ngoai handleTextMessage,
        // khien Spring (ExceptionWebSocketHandlerDecorator that, khong xuat hien trong
        // test nay vi goi handler truc tiep) dong luon session cua chinh nguoi gui.
        assertDoesNotThrow(() ->
                handler.handleTextMessage(session, new TextMessage("{ day khong phai JSON hop le")));

        assertTrue(session.isOpen(), "Session khong duoc tu dong bi dong chi vi 1 ban tin loi");
    }

    @Test
    void missingTypeFieldIsIgnoredWithoutThrowing() {
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession session = new FakeWebSocketSession("s1");

        assertDoesNotThrow(() ->
                handler.handleTextMessage(session, new TextMessage("{\"roomId\":\"r1\"}")));
    }

    @Test
    void oneFailedSendDuringBroadcastDoesNotBlockNotifyingOtherPeers() throws Exception {
        RoomRegistry roomRegistry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(roomRegistry, objectMapper);

        // Gia lap socket dang dong dot ngot: isOpen() van bao true (chua kip cap nhat
        // trang thai) nhung sendMessage() nem IOException khi thuc su goi gui - dung
        // theo dung tinh huong "Failed to receive/send: Socket closed" da thay that
        // trong log luc chay IceP2pConnectionEstablisherTest o phien lam viec truoc.
        FakeWebSocketSession brokenPeerSession = new FakeWebSocketSession("broken-session");
        brokenPeerSession.failSendsWith(new IOException("gia lap socket dong dot ngot"));
        FakeWebSocketSession healthyPeerSession = new FakeWebSocketSession("healthy-session");

        // Dua 2 peer nay vao registry THAT truoc (mo phong ho da JOIN tu truoc) - thu tu
        // co y: brokenPeer dung TRUOC healthyPeer trong danh sach, de nếu loi cua no lam
        // dung vong lap broadcastToOthers (loi cu chua sua) thi healthyPeer se khong bao
        // gio duoc goi toi.
        roomRegistry.join(new PeerSession(brokenPeerSession, "room-1", "broken-peer", "Broken"));
        roomRegistry.join(new PeerSession(healthyPeerSession, "room-1", "healthy-peer", "Healthy"));

        FakeWebSocketSession joiningSession = new FakeWebSocketSession("joining");
        SignalMessage join = SignalMessage.join("room-1", "new-peer", "New");

        assertDoesNotThrow(() ->
                handler.handleTextMessage(joiningSession, new TextMessage(objectMapper.writeValueAsString(join))));

        assertFalse(healthyPeerSession.getSentMessages().isEmpty(),
                "healthyPeerSession phai van nhan duoc thong bao du brokenPeerSession gui that bai truoc no");
    }
}
