package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.model.ChatMessage;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nhieu {@link P2pChatSession} THAT (ICE tren may local) qua signaling-server THAT:
 * lich su cho nguoi moi vao phong, va phong co mat khau.
 */
class P2pChatSessionIntegrationTest {

    private static ConfigurableApplicationContext server;
    private static String serverUri;
    private final List<P2pChatSession> sessions = new ArrayList<>();

    @BeforeAll
    static void startServer() throws Exception {
        // Khoa danh tinh / cau hinh client ghi vao thu muc tam, khong dung toi ~ that.
        System.setProperty("user.home", Files.createTempDirectory("chat-p2p-it").toString());
        server = new SpringApplicationBuilder(SignalingServerApplication.class)
                .run("--server.port=0", "--chat-p2p.auth.required=false");
        int port = ((ServletWebServerApplicationContext) server).getWebServer().getPort();
        serverUri = "ws://localhost:" + port;
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @AfterEach
    void leaveAll() {
        sessions.forEach(P2pChatSession::leave);
    }

    /** Ghi lai su kien cua 1 client. */
    static final class Recorder implements ChatSessionListener {
        final Set<String> joined = ConcurrentHashMap.newKeySet();
        final Set<String> verified = ConcurrentHashMap.newKeySet();
        final List<String> messages = new CopyOnWriteArrayList<>();
        final List<String> history = new CopyOnWriteArrayList<>();
        final Map<String, String> historyAuthors = new ConcurrentHashMap<>();
        final List<String> failures = new CopyOnWriteArrayList<>();

        @Override
        public void onPeerJoined(SessionPeer peer) {
            joined.add(peer.peerId());
        }

        @Override
        public void onPeerIdentityUpdated(SessionPeer peer) {
            verified.add(peer.peerId());
        }

        @Override
        public void onPeerLeft(String peerId) {
            joined.remove(peerId);
        }

        @Override
        public void onMessageReceived(ChatMessage message) {
            messages.add(message.getText());
        }

        @Override
        public void onHistoryReceived(String fromPeerId, List<ChatMessage> messages, Map<String, String> authorNames) {
            messages.forEach(m -> history.add(m.getText()));
            historyAuthors.putAll(authorNames);
        }

        @Override
        public void onPeerConnectionFailed(String peerId, String reason) {
            failures.add(reason);
        }
    }

    private P2pChatSession join(String user, String room, String password, Recorder recorder) {
        P2pChatSession session = new P2pChatSession(new RoomConfig(user, room, serverUri, "", password));
        sessions.add(session);
        session.start(recorder);
        return session;
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

    @Test
    void latecomerReceivesGroupHistoryThenLiveMessages() throws Exception {
        String room = "it-history-" + System.nanoTime();
        Recorder alice = new Recorder();
        P2pChatSession aliceSession = join("Alice", room, "", alice);
        aliceSession.sendGroupMessage(new ChatMessage(aliceSession.selfPeerId(), "tin 1 truoc khi Bob vao"));
        aliceSession.sendGroupMessage(new ChatMessage(aliceSession.selfPeerId(), "tin 2 **markdown**"));

        Recorder bob = new Recorder();
        P2pChatSession bobSession = join("Bob", room, "", bob);

        assertTrue(await(() -> bob.history.size() == 2, 30),
                "Bob phai nhan 2 tin lich su, thuc te: " + bob.history + " loi: " + bob.failures);
        assertEquals(List.of("tin 1 truoc khi Bob vao", "tin 2 **markdown**"), bob.history);
        assertEquals("Alice", bob.historyAuthors.get(aliceSession.selfPeerId()));
        assertTrue(alice.history.isEmpty(), "Bob chua co lich su nen Alice khong nhan gi");

        aliceSession.sendGroupMessage(new ChatMessage(aliceSession.selfPeerId(), "tin truc tiep"));
        assertTrue(await(() -> bob.messages.contains("tin truc tiep"), 10));
        assertTrue(bobSession.selfPeerId() != null);
    }

    @Test
    void onlyPeersWithSamePasswordSeeEachOther() throws Exception {
        String room = "it-private-" + System.nanoTime();
        Recorder alice = new Recorder();
        Recorder bob = new Recorder();
        Recorder mallory = new Recorder();
        Recorder publicGuest = new Recorder();
        P2pChatSession aliceSession = join("Alice", room, "mat-khau-dung", alice);
        P2pChatSession bobSession = join("Bob", room, "mat-khau-dung", bob);
        join("Mallory", room, "doan-sai", mallory);
        join("Guest", room, "", publicGuest);

        assertTrue(await(() -> alice.joined.contains(bobSession.selfPeerId())
                        && bob.joined.contains(aliceSession.selfPeerId()), 30),
                "Cung mat khau phai thay nhau; loi: " + alice.failures + bob.failures);
        assertTrue(await(() -> alice.verified.contains(bobSession.selfPeerId()), 15), "Danh tinh van duoc xac thuc");

        aliceSession.sendGroupMessage(new ChatMessage(aliceSession.selfPeerId(), "bi mat"));
        assertTrue(await(() -> bob.messages.contains("bi mat"), 10));

        Thread.sleep(3_000); // cho du lau de neu co ro ri thi nguoi sai mat khau da ket noi
        assertEquals(Set.of(bobSession.selfPeerId()), alice.joined, "Alice chi thay Bob");
        assertTrue(mallory.joined.isEmpty() && mallory.messages.isEmpty(), "Sai mat khau khong thay ai, khong nhan tin");
        assertTrue(publicGuest.joined.isEmpty() && publicGuest.messages.isEmpty(),
                "Vao cung ten phong nhung khong mat khau cung khong thay ai");
    }
}
