package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
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
    void joinMissingRequiredFieldsIsIgnoredWithoutThrowingOrClosingTheSession() throws Exception {
        // PeerSession (module signaling-server) dung Objects.requireNonNull cho ca
        // roomId/peerId/userName - neu handleJoin khong tu validate truoc, 1 ban
        // tin JOIN hop le VE MAT JSON nhung thieu fromPeerId (vd bug client, hoac
        // co y tan cong) se nem NullPointerException NGAY TRONG handleTextMessage,
        // KHONG duoc bat o dau ca (khac voi loi parse JSON da xu ly rieng o
        // malformedJsonIsIgnoredWithoutThrowingOrClosingTheSession) - vi pham dung
        // nguyen tac H.1 da ap dung cho cac loai loi khac trong lop nay.
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(new RoomRegistry(), objectMapper);
        FakeWebSocketSession session = new FakeWebSocketSession("s1");

        SignalMessage joinMissingFromPeerId = new SignalMessage();
        joinMissingFromPeerId.setType(SignalType.JOIN);
        joinMissingFromPeerId.setRoomId("room-1");
        // Co y KHONG set fromPeerId/userName - mo phong dung ban tin JOIN thieu truong.
        String json = objectMapper.writeValueAsString(joinMissingFromPeerId);

        assertDoesNotThrow(() -> handler.handleTextMessage(session, new TextMessage(json)),
                "JOIN thieu fromPeerId/userName khong duoc lam nem NullPointerException ra ngoai handleTextMessage");
        assertTrue(session.isOpen(), "Session khong duoc tu dong bi dong chi vi 1 ban tin JOIN thieu truong");
    }

    @Test
    void joiningTwiceOnTheSameSessionWithoutLeavingCleansUpThePreviousRoomEntry() throws Exception {
        // Khong co gi ngan 1 client (bug hoac co y) gui JOIN lan 2 tren CUNG 1
        // session ma khong gui LEAVE truoc - vd doi phong giua chung. Neu
        // RoomRegistry khong tu don entry cu, no se giu ca 2 entry cho CUNG 1
        // session; RoomRegistry#leaveBySession (goi luc session dong that su) chi
        // tim va xoa DUNG entry DAU TIEN gap - entry con lai "mo coi" vinh vien,
        // khong bao gio duoc thong bao PEER_LEFT toi phong cu.
        RoomRegistry roomRegistry = new RoomRegistry();
        SignalingWebSocketHandler handler = new SignalingWebSocketHandler(roomRegistry, objectMapper);
        FakeWebSocketSession session = new FakeWebSocketSession("s1");

        handler.handleTextMessage(session, new TextMessage(
                objectMapper.writeValueAsString(SignalMessage.join("room-A", "p1", "Peer1"))));
        assertFalse(roomRegistry.peersInRoom("room-A").isEmpty(), "Sau JOIN lan 1, room-A phai co peer");

        // Gui JOIN lan 2 tren CUNG session, sang phong KHAC - khong gui LEAVE truoc.
        handler.handleTextMessage(session, new TextMessage(
                objectMapper.writeValueAsString(SignalMessage.join("room-B", "p1", "Peer1"))));

        assertTrue(roomRegistry.peersInRoom("room-A").isEmpty(),
                "Entry cu o room-A phai duoc don dep khi JOIN lan 2 sang phong khac tren cung session");
        assertFalse(roomRegistry.peersInRoom("room-B").isEmpty(), "room-B phai co peer sau JOIN lan 2");
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
