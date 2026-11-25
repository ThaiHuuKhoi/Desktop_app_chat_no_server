package com.datn.chatp2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Heartbeat tren server chay that, voi client WebSocket cua JDK - dung loai client ma app dung
 * ({@code WebSocketSignalingClient}). Chu ky rut ngan con 200 ms / het han 1 s de test nhanh.
 *
 * <ul>
 *   <li>Client JDK binh thuong tu tra Pong, ke ca client cu khong biet gi ve heartbeat - khong bi da oan.</li>
 *   <li>Client "chet" (khong xu ly frame nao, giong may mat Wi-Fi) bi phat hien va phong nhan PEER_LEFT.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "chat-p2p.ws.heartbeat-interval-ms=200",
        "chat-p2p.ws.heartbeat-timeout-ms=1000",
        // Context rieng (khac thuoc tinh) -> CSDL H2 rieng, khong chay lai schema.sql tren CSDL cua test khac.
        "spring.datasource.url=jdbc:h2:mem:heartbeattest;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class HeartbeatRealConnectionTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jdkClientAnswersPingsAutomaticallyWhileDeadClientIsDetected() throws Exception {
        String room = "heartbeat-" + System.nanoTime();
        URI uri = URI.create("ws://localhost:" + port + "/ws");
        HttpClient http = HttpClient.newHttpClient();

        BlockingQueue<SignalMessage> aliceInbox = new LinkedBlockingQueue<>();
        CountDownLatch aliceClosed = new CountDownLatch(1);
        WebSocket alice = http.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    try {
                        aliceInbox.add(objectMapper.readValue(buffer.toString(), SignalMessage.class));
                    } catch (Exception ignored) {
                        // bo qua ban tin khong doc duoc
                    }
                    buffer.setLength(0);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                aliceClosed.countDown();
                return null;
            }
        }).get(5, TimeUnit.SECONDS);

        // "Dead" khong bao gio request() frame nao -> khong doc Ping, khong tra Pong (nhu may vua mat Wi-Fi),
        // nhung van gui duoc JOIN.
        WebSocket dead = http.newWebSocketBuilder().buildAsync(uri, new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                // co y khong goi webSocket.request(...)
            }
        }).get(5, TimeUnit.SECONDS);

        alice.sendText(objectMapper.writeValueAsString(SignalMessage.join(room, "alice", "Alice")), true).join();
        assertEquals(SignalType.PEER_LIST, aliceInbox.poll(5, TimeUnit.SECONDS).getType());
        dead.sendText(objectMapper.writeValueAsString(SignalMessage.join(room, "dead", "Dead")), true).join();
        assertEquals(SignalType.PEER_JOINED, aliceInbox.poll(5, TimeUnit.SECONDS).getType());

        SignalMessage left = aliceInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(left, "Phong phai nhan PEER_LEFT cua client khong tra Pong");
        assertEquals(SignalType.PEER_LEFT, left.getType());
        assertEquals("dead", left.getFromPeerId());

        // Alice da song qua hon 10 chu ky Ping chi nho JDK tu tra Pong.
        assertFalse(aliceClosed.await(2, TimeUnit.SECONDS), "Client JDK binh thuong khong duoc bi dong");

        alice.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        dead.abort();
    }
}
