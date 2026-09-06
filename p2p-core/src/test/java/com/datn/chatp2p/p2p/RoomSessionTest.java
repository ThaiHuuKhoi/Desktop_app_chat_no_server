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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra toan bo vong doi cua {@link RoomSession} giua 2 peer - dung theo
 * dung goi y "test 2 instance tren cung may" o Tai-lieu-ky-thuat.md Phan
 * F.5.2, chi thay the phan signaling bang {@link LoopbackSignalingClient}
 * (mo phong dung hanh vi cua signaling-server that trong bo nho) de khong
 * can chay Spring Boot that trong 1 unit test cua module p2p-core.
 *
 * <p>Moi thu khac deu la hang that: {@code IceP2pConnectionEstablisher}
 * chay ice4j that (Agent that, connectivity check that tren localhost),
 * {@code PeerConnection} trao khoa ECDH that (module crypto that), va
 * {@code EnvelopeCodec} ma hoa AES-GCM that.
 */
class RoomSessionTest {

    @Test
    void twoRoomSessionsDiscoverEachOtherAndExchangeAnEncryptedMessage() throws Exception {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();

        RoomSession alice = new RoomSession(
                "room-1", "alice", "Alice", new LoopbackSignalingClient(hub), List.of());
        RoomSession bob = new RoomSession(
                "room-1", "bob", "Bob", new LoopbackSignalingClient(hub), List.of());

        CountDownLatch bothJoined = new CountDownLatch(2);
        AtomicReference<PeerConnection> aliceSideOfBob = new AtomicReference<>();
        AtomicReference<PeerConnection> bobSideOfAlice = new AtomicReference<>();
        AtomicReference<Throwable> anyFailure = new AtomicReference<>();

        alice.onPeerJoined(connection -> {
            aliceSideOfBob.set(connection);
            bothJoined.countDown();
        });
        bob.onPeerJoined(connection -> {
            bobSideOfAlice.set(connection);
            bothJoined.countDown();
        });
        alice.onConnectionFailed((peerId, error) -> anyFailure.set(error));
        bob.onConnectionFailed((peerId, error) -> anyFailure.set(error));

        BlockingQueue<Envelope> bobInbox = new ArrayBlockingQueue<>(10);
        bob.onEnvelope(EnvelopeType.MESSAGE, (fromPeerId, envelope) -> bobInbox.add(envelope));

        // Alice vao truoc (phong trong), Bob vao sau -> Bob thay Alice qua PEER_LIST
        // nen Bob se chu dong gui OFFER; Alice chi nhan duoc PEER_JOINED va cho OFFER toi.
        alice.join("ws://fake-signaling-server/ws");
        bob.join("ws://fake-signaling-server/ws");

        boolean connectedInTime = bothJoined.await(15, TimeUnit.SECONDS);
        if (anyFailure.get() != null) {
            throw new AssertionError("RoomSession bao loi ket noi truoc khi ca 2 ben join xong", anyFailure.get());
        }
        assertTrue(connectedInTime, "Ca 2 RoomSession phai bao onPeerJoined trong 15s tren localhost");

        assertEquals("bob", aliceSideOfBob.get().getPeerId());
        assertEquals("alice", bobSideOfAlice.get().getPeerId());
        assertEquals(List.of("bob"), alice.getPeerIds());
        assertEquals(List.of("alice"), bob.getPeerIds());

        MessagePayload sentMessage = new MessagePayload("m-1", "alice", "Chao Bob qua RoomSession that", 42L);
        alice.sendTo("bob", EnvelopeType.MESSAGE, sentMessage);

        Envelope received = bobInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Bob phai nhan duoc Envelope tu Alice trong 5s");
        assertEquals(EnvelopeType.MESSAGE, received.type());

        // Bob tu parse lai bang chinh ObjectMapper/khoa cua minh (thong qua PeerConnection
        // noi bo cua RoomSession) - o day chi con cach de kiem tra noi dung la doc lai
        // qua object Envelope da nhan (payload van la JSON tho ben trong).
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        MessagePayload parsed = objectMapper.readValue(received.payload(), MessagePayload.class);
        assertEquals(sentMessage, parsed);

        alice.leave();
        bob.leave();
    }
}
