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
