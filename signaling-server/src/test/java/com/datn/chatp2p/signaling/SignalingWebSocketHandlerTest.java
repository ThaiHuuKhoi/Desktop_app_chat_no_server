package com.datn.chatp2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra signaling server chay that (khong chi bien dich): mo ket noi WebSocket
 * that, gui JOIN, xac nhan nhan lai PEER_LIST; peer thu hai vao phong phai lam
 * peer dau tien nhan duoc PEER_JOINED.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalingWebSocketHandlerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void joiningARoomReturnsPeerListAndNotifiesExistingPeers() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String wsUri = "ws://localhost:" + port + "/ws";
        String roomId = "test-room-" + System.nanoTime();

        BlockingQueue<SignalMessage> alicesInbox = new ArrayBlockingQueue<>(10);
        WebSocketSession alice = client
                .execute(new CapturingHandler(alicesInbox), wsUri)
                .get(5, TimeUnit.SECONDS);

        send(alice, SignalMessage.join(roomId, "alice", "Alice"));
        SignalMessage aliceJoinAck = alicesInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(aliceJoinAck);
        assertEquals(SignalType.PEER_LIST, aliceJoinAck.getType());
        assertTrue(aliceJoinAck.getPeers().isEmpty(), "Phong moi nen chua co peer nao khac");

        BlockingQueue<SignalMessage> bobsInbox = new ArrayBlockingQueue<>(10);
        WebSocketSession bob = client
                .execute(new CapturingHandler(bobsInbox), wsUri)
                .get(5, TimeUnit.SECONDS);
        send(bob, SignalMessage.join(roomId, "bob", "Bob"));

        SignalMessage bobJoinAck = bobsInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(bobJoinAck);
        assertEquals(SignalType.PEER_LIST, bobJoinAck.getType());
        assertEquals(1, bobJoinAck.getPeers().size(), "Bob phai thay Alice da co san trong phong");
        assertEquals("alice", bobJoinAck.getPeers().get(0).getPeerId());

        SignalMessage peerJoinedNotice = alicesInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(peerJoinedNotice, "Alice phai nhan duoc thong bao PEER_JOINED khi Bob vao phong");
        assertEquals(SignalType.PEER_JOINED, peerJoinedNotice.getType());
        assertEquals("bob", peerJoinedNotice.getFromPeerId());

        alice.close();
        bob.close();
    }

    @Test
    void relaysALargeIceOfferPayloadOverARealWebSocketConnection() throws Exception {
        // Test SignalingServerContentOpacityTest#relaysVeryLargePayloadWithoutTruncationOrError
        // (module ws) da chung minh handler KHONG tu cat bot payload lon - nhung
        // test do goi thang handler.handleTextMessage() voi 1 FakeWebSocketSession,
        // bo qua HOAN TOAN Tomcat/WsSession that voi gioi han buffer mac dinh
        // (maxTextMessageBufferSize = 8192 byte). Test nay dung ket noi WebSocket
        // THAT (qua StandardWebSocketClient nhu joiningARoomReturnsPeerListAndNotifiesExistingPeers
        // o tren) de xac nhan claim do van dung khi di qua Tomcat that, khong chi
        // qua goi ham truc tiep.
        //
        // Gioi han buffer 8192 byte mac dinh cua Tomcat ton tai o CA 2 PHIA doc
        // lap nhau: WebSocketConfig#createWebSocketContainer da tang gioi han
        // phia SERVER (nhan tu client) - nhung StandardWebSocketClient dung
        // container CLIENT rieng (ContainerProvider.getWebSocketContainer(), instance
        // khac voi container cua server), CUNG mac dinh 8192 cho chieu nhan tu
        // server ve client (luc server relay lai cho Bob). Phai tang ca 2 - thieu
        // 1 trong 2 phia van se that bai o dung chieu con lai.
        WebSocketContainer clientContainer = ContainerProvider.getWebSocketContainer();
        clientContainer.setDefaultMaxTextMessageBufferSize(65536);
        StandardWebSocketClient client = new StandardWebSocketClient(clientContainer);
        String wsUri = "ws://localhost:" + port + "/ws";
        String roomId = "test-room-large-" + System.nanoTime();

        BlockingQueue<SignalMessage> alicesInbox = new ArrayBlockingQueue<>(10);
        WebSocketSession alice = client.execute(new CapturingHandler(alicesInbox), wsUri).get(5, TimeUnit.SECONDS);
        send(alice, SignalMessage.join(roomId, "alice", "Alice"));
        assertNotNull(alicesInbox.poll(5, TimeUnit.SECONDS)); // PEER_LIST rong, bo qua noi dung

        BlockingQueue<SignalMessage> bobsInbox = new ArrayBlockingQueue<>(10);
        WebSocketSession bob = client.execute(new CapturingHandler(bobsInbox), wsUri).get(5, TimeUnit.SECONDS);
        send(bob, SignalMessage.join(roomId, "bob", "Bob"));
        assertNotNull(bobsInbox.poll(5, TimeUnit.SECONDS)); // PEER_LIST co Alice, bo qua noi dung
        assertNotNull(alicesInbox.poll(5, TimeUnit.SECONDS)); // PEER_JOINED cua Bob, bo qua noi dung

        // Mo phong 1 IceOfferPayload thuc te co NHIEU candidate (may co nhieu adapter
        // mang ao - VPN, Docker, WSL, Hyper-V... hoan toan co that) - du de vuot qua
        // 8KB mac dinh cua Tomcat neu server chua duoc cau hinh rieng.
        String largePayload = "x".repeat(20_000);
        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setRoomId(roomId);
        offer.setFromPeerId("alice");
        offer.setToPeerId("bob");
        offer.setPayload(largePayload);
        send(alice, offer);

        SignalMessage relayed = bobsInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(relayed, "Bob phai nhan duoc OFFER voi payload lon - khong bi Tomcat tu dong dong ket noi/cat bot");
        assertEquals(largePayload.length(), relayed.getPayload().length(),
                "Payload lon khong duoc bi cat bot khi di qua ket noi WebSocket THAT");

        alice.close();
        bob.close();
    }

    private void send(WebSocketSession session, SignalMessage message) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private class CapturingHandler extends TextWebSocketHandler {
        private final BlockingQueue<SignalMessage> inbox;

        CapturingHandler(BlockingQueue<SignalMessage> inbox) {
            this.inbox = inbox;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            inbox.add(objectMapper.readValue(message.getPayload(), SignalMessage.class));
        }
    }
}
