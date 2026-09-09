package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.protocol.Envelope;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.datn.chatp2p.p2p.signaling.WebSocketSignalingClient;
import com.datn.chatp2p.signaling.SignalingServerApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

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
 * Giong het {@link RoomSessionTest} ve mat kich ban, nhung dung
 * {@link WebSocketSignalingClient} THAT ket noi toi 1 instance
 * {@code signaling-server} THAT (tu boot bang {@link SpringApplicationBuilder}
 * tren 1 cong ngau nhien, khong dung {@code @SpringBootTest} de khong phai
 * keo them {@code spring-boot-starter-test}) - xac nhan ca duong WebSocket
 * that (JSON that qua mang localhost that) hoat dong dung, khong chi logic
 * noi bo cua {@link RoomSession} nhu ban test dung {@code LoopbackSignalingClient}.
 *
 * <p>Day la dependency CHI-TEST cua {@code p2p-core} len {@code signaling-server}
 * (xem {@code p2p-core/pom.xml}) - khong anh huong toi jar chinh, khong dao
 * nguoc kien truc (signaling-server van khong biet gi ve p2p-core/ice4j/crypto).
 */
class RoomSessionRealSignalingServerTest {

    private ConfigurableApplicationContext serverContext;
    private String signalingServerUri;

    @BeforeEach
    void startRealSignalingServer() {
        // LUU Y: builder.properties("server.port=0") KHONG hoat dong o day - no
        // dang ky voi do uu tien THAP NHAT (defaultProperties), trong khi
        // application.yml cua signaling-server da hardcode server.port=8080 voi
        // do uu tien cao hon, nen properties() bi de. Phai dung run(String...)
        // (tuong duong tham so dong lenh "--server.port=0"), co do uu tien cao
        // nhat, moi ghi de duoc gia tri trong application.yml.
        serverContext = new SpringApplicationBuilder(SignalingServerApplication.class)
                .run("--server.port=0"); // 0 = de he dieu hanh tu chon 1 cong trong, tranh dung do 8080
        int port = ((ServletWebServerApplicationContext) serverContext).getWebServer().getPort();
        signalingServerUri = "ws://localhost:" + port;
    }

    @AfterEach
    void stopRealSignalingServer() {
        if (serverContext != null) {
            serverContext.close();
        }
    }

    @Test
    void twoRoomSessionsConnectThroughRealSignalingServerAndExchangeAnEncryptedMessage() throws Exception {
        RoomSession alice = new RoomSession(
                "room-real-1", "alice", "Alice", new WebSocketSignalingClient(), List.of());
        RoomSession bob = new RoomSession(
                "room-real-1", "bob", "Bob", new WebSocketSignalingClient(), List.of());

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

        // Khac RoomSessionTest (fake, dong bo): o day connect() that qua WebSocket
        // that su gui goi tin qua mang (localhost) va cac ban tin PEER_LIST/OFFER/
        // ANSWER... den bat dong bo tren thread rieng cua HttpClient, khong con
        // chay long nhau dong bo trong 1 loi goi join() nhu ban fake.
        alice.join(signalingServerUri);
        bob.join(signalingServerUri);

        boolean connectedInTime = bothJoined.await(15, TimeUnit.SECONDS);
        if (anyFailure.get() != null) {
            throw new AssertionError("RoomSession bao loi ket noi qua signaling-server that", anyFailure.get());
        }
        assertTrue(connectedInTime, "Ca 2 RoomSession phai ket noi xong qua signaling-server THAT trong 15s");

        assertEquals("bob", aliceSideOfBob.get().getPeerId());
        assertEquals("alice", bobSideOfAlice.get().getPeerId());
        assertEquals(List.of("bob"), alice.getPeerIds());
        assertEquals(List.of("alice"), bob.getPeerIds());

        MessagePayload sentMessage = new MessagePayload("m-real-1", "alice", "Chao Bob qua signaling server that", 7L);
        alice.sendTo("bob", EnvelopeType.MESSAGE, sentMessage);

        Envelope received = bobInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "Bob phai nhan duoc Envelope tu Alice trong 5s");
        assertEquals(EnvelopeType.MESSAGE, received.type());

        ObjectMapper objectMapper = new ObjectMapper();
        MessagePayload parsed = objectMapper.readValue(received.payload(), MessagePayload.class);
        assertEquals(sentMessage, parsed);

        alice.leave();
        bob.leave();
    }
}
