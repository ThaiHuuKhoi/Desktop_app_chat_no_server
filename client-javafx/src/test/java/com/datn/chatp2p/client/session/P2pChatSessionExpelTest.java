package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.protocol.EnvelopeType;
import com.datn.chatp2p.common.protocol.RoomProofPayload;
import com.datn.chatp2p.p2p.PeerConnection;
import com.datn.chatp2p.p2p.RoomSession;
import com.datn.chatp2p.p2p.signaling.WebSocketSignalingClient;
import com.datn.chatp2p.signaling.SignalingServerApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nguoi khong chung minh duoc mat khau phong bi NGAT HAN (truoc day chi bi bo qua ban tin, duong UDP van mo).
 * "Ke la" o day la 1 {@link RoomSession} tran (khong qua P2pChatSession) vao dung roomId "ten~the" cua phong -
 * mo phong signaling server bi chiem nhet nguoi la vao phong.
 */
class P2pChatSessionExpelTest {

    private static ConfigurableApplicationContext server;
    private static String serverUri;
    private final List<P2pChatSession> sessions = new ArrayList<>();
    private final List<RoomSession> intruders = new ArrayList<>();

    @BeforeAll
    static void startServer() throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("chat-p2p-expel").toString());
        server = new SpringApplicationBuilder(SignalingServerApplication.class)
                .run("--server.port=0", "--chat-p2p.auth.required=false");
        serverUri = "ws://localhost:" + ((ServletWebServerApplicationContext) server).getWebServer().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @AfterEach
    void leaveAll() {
        sessions.forEach(P2pChatSession::leave);
        intruders.forEach(RoomSession::leave);
    }

    static final class Recorder implements ChatSessionListener {
        final Set<String> joined = ConcurrentHashMap.newKeySet();
        final List<String> left = new CopyOnWriteArrayList<>();
        final List<String> failures = new CopyOnWriteArrayList<>();
        final List<String> rejections = new CopyOnWriteArrayList<>();

        @Override
        public void onPeerJoined(SessionPeer peer) {
            joined.add(peer.peerId());
        }

        @Override
        public void onPeerLeft(String peerId) {
            joined.remove(peerId);
            left.add(peerId);
        }

        @Override
        public void onMessageReceived(ChatMessage message) {
        }

        @Override
        public void onJoinRejected(String reason) {
            rejections.add(reason);
        }

        @Override
        public void onPeerConnectionFailed(String peerId, String reason) {
            failures.add(reason);
        }
    }

    @Test
    void intruderWithInvalidRoomProofIsDisconnectedAndBlocked() throws Exception {
        Recorder alice = new Recorder();
        P2pChatSession aliceSession = join("Alice", "it-expel-" + System.nanoTime(), "mat-khau-dung", alice);

        String intruderId = "intruder-" + UUID.randomUUID();
        List<PeerConnection> intruderSide = new CopyOnWriteArrayList<>();
        RoomSession intruder = intruder(intruderId, aliceSession.storageKey());
        intruder.onPeerJoined(connection -> {
            intruderSide.add(connection);
            repeat(() -> connection.send(EnvelopeType.ROOM_PROOF,
                    new RoomProofPayload(Base64.getEncoder().encodeToString(new byte[32]))));
        });
        intruder.join(serverUri);

        assertTrue(await(() -> aliceSession.isExpelled(intruderId), 30),
                "Bang chung sai phai bi ngat; loi: " + alice.failures);
        assertFalse(intruderSide.isEmpty(), "Ke la da trao khoa ECDH xong truoc khi bi ngat");
        assertFalse(aliceSession.isConnectedTo(intruderId), "Duong P2P toi ke la phai bi dong");
        assertTrue(alice.joined.isEmpty(), "Ke la khong bao gio hien tren giao dien");
        assertTrue(alice.failures.stream().anyMatch(f -> f.contains("không có đúng mật khẩu phòng")
                && f.contains("đã ngắt kết nối")), "Phai bao ly do ngat: " + alice.failures);
    }

    @Test
    void intruderThatNeverSendsRoomProofIsDisconnectedAfterTimeout() throws Exception {
        Recorder alice = new Recorder();
        P2pChatSession aliceSession = new P2pChatSession(new RoomConfig("Alice", "it-silent-" + System.nanoTime(),
                serverUri, "", "mat-khau-dung"));
        aliceSession.roomProofTimeoutMillis = 1_500;
        sessions.add(aliceSession);
        aliceSession.start(alice);

        String intruderId = "silent-" + UUID.randomUUID();
        List<PeerConnection> intruderSide = new CopyOnWriteArrayList<>();
        RoomSession intruder = intruder(intruderId, aliceSession.storageKey());
        intruder.onPeerJoined(intruderSide::add); // trao khoa xong roi im lang, khong gui ROOM_PROOF
        intruder.join(serverUri);

        assertTrue(await(() -> !intruderSide.isEmpty(), 30), "Ke la phai trao khoa ECDH xong");
        assertTrue(await(() -> aliceSession.isExpelled(intruderId), 10),
                "Khong gui bang chung trong thoi han phai bi ngat; loi: " + alice.failures);
        assertFalse(aliceSession.isConnectedTo(intruderId));
        assertTrue(alice.failures.stream().anyMatch(f -> f.contains("không gửi bằng chứng mật khẩu phòng")),
                "Phai bao ly do: " + alice.failures);
    }

    @Test
    void peerWithCorrectPasswordIsStillAdmitted() throws Exception {
        String room = "it-ok-" + System.nanoTime();
        Recorder alice = new Recorder();
        Recorder bob = new Recorder();
        P2pChatSession aliceSession = join("Alice", room, "mat-khau-dung", alice);
        P2pChatSession bobSession = join("Bob", room, "mat-khau-dung", bob);

        assertTrue(await(() -> alice.joined.contains(bobSession.selfPeerId())
                && bob.joined.contains(aliceSession.selfPeerId()), 30), "Dung mat khau van thay nhau");
        Thread.sleep(P2pChatSession.ROOM_PROOF_TIMEOUT_MILLIS / 5);
        assertFalse(aliceSession.isExpelled(bobSession.selfPeerId()));
        assertTrue(aliceSession.isConnectedTo(bobSession.selfPeerId()));
    }

    // ------------------------------------------------------------ helpers

    private P2pChatSession join(String user, String room, String password, Recorder recorder) {
        P2pChatSession session = new P2pChatSession(new RoomConfig(user, room, serverUri, "", password));
        sessions.add(session);
        session.start(recorder);
        return session;
    }

    private RoomSession intruder(String peerId, String serverRoomId) {
        RoomSession intruder = new RoomSession(serverRoomId, peerId, "Ke la", new WebSocketSignalingClient(), List.of());
        intruders.add(intruder);
        return intruder;
    }

    /**
     * Gui ngay roi gui lai sau 0,5 s va 1,5 s, nhu app that gui lai ROOM_PROOF / PEER_IDENTITY: goi dau co the
     * toi truoc khi ben kia trao khoa xong va bi bo. Loi gui (ket noi da bi dong) thi thoi.
     */
    private static void repeat(Runnable send) {
        Thread thread = new Thread(() -> {
            for (long delay : new long[] {0, 500, 1_000}) {
                try {
                    Thread.sleep(delay);
                    send.run();
                } catch (InterruptedException e) {
                    return;
                } catch (RuntimeException closed) {
                    return;
                }
            }
        }, "intruder-resend");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean await(BooleanSupplier condition, long seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }
}
