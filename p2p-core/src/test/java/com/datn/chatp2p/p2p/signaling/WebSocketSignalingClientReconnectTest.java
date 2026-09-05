package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.signaling.SignalingServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xac nhan {@link WebSocketSignalingClient} tu dong ket noi lai sau khi
 * signaling-server "sap" bat thuong (mo phong bang cach dong thang
 * {@link ConfigurableApplicationContext}, khong phai goi
 * {@code disconnect()} tu phia client) - Tai-lieu-ky-thuat.md Phan H.3.
 *
 * <p>Kich ban: Alice ket noi thanh cong -> server "sap" -> Alice tu phat
 * hien mat ket noi (chuyen sang {@code RECONNECTING}) -> server song lai
 * TREN CUNG CONG -> Alice tu ket noi lai va TU GUI LAI JOIN -> xac nhan
 * bang cach cho Bob vao phong SAU khi server song lai va kiem tra Alice
 * co nhan duoc {@code PEER_JOINED} cua Bob khong (chi co the neu Alice da
 * that su re-join thanh cong vao phong tren server moi).
 */
class WebSocketSignalingClientReconnectTest {

    @Test
    void reconnectsAutomaticallyAfterServerRestartAndRejoinsRoom() throws Exception {
        int port = findFreePort();
        String signalingServerUri = "ws://localhost:" + port;

        ConfigurableApplicationContext serverContext = startServerOnPort(port);

        WebSocketSignalingClient alice = new WebSocketSignalingClient();
        CopyOnWriteArrayList<WebSocketSignalingClient.ConnectionState> aliceStates = new CopyOnWriteArrayList<>();
        AtomicInteger connectedCount = new AtomicInteger(0);
        CountDownLatch reconnectingLatch = new CountDownLatch(1);
        CountDownLatch reconnectedLatch = new CountDownLatch(1);

        alice.onConnectionStateChanged(state -> {
            aliceStates.add(state);
            if (state == WebSocketSignalingClient.ConnectionState.RECONNECTING) {
                reconnectingLatch.countDown();
            } else if (state == WebSocketSignalingClient.ConnectionState.CONNECTED) {
                if (connectedCount.incrementAndGet() == 2) {
                    reconnectedLatch.countDown();
                }
            }
        });

        CountDownLatch alicePeerListReceived = new CountDownLatch(1);
        alice.onPeerList(message -> alicePeerListReceived.countDown());
        CountDownLatch alicePeerJoinedNotice = new CountDownLatch(1);
        alice.onPeerJoined(message -> alicePeerJoinedNotice.countDown());

        alice.connect(signalingServerUri, "room-reconnect", "alice", "Alice");
        assertTrue(alicePeerListReceived.await(5, TimeUnit.SECONDS), "Alice phai ket noi va JOIN thanh cong lan dau");

        // Mo phong server "sap" bat thuong - dong thang context, KHONG phai
        // alice.disconnect() (truong hop do se khong tu reconnect, dung thiet ke).
        serverContext.close();

        assertTrue(reconnectingLatch.await(10, TimeUnit.SECONDS),
                "Alice phai tu phat hien mat ket noi va chuyen sang RECONNECTING");

        // Server song lai tren DUNG cong cu.
        ConfigurableApplicationContext serverContextAfterRestart = startServerOnPort(port);
        try {
            assertTrue(reconnectedLatch.await(30, TimeUnit.SECONDS),
                    "Alice phai tu ket noi lai thanh cong (CONNECTED lan 2) trong 30s ke tu luc server song lai");

            // Bob vao phong SAU khi server song lai - Alice chi thay duoc PEER_JOINED
            // neu da that su re-JOIN thanh cong vao phong tren server MOI (registry moi,
            // khong con nho gi ve lan JOIN truoc do khi server cu da sap).
            WebSocketSignalingClient bob = new WebSocketSignalingClient();
            bob.connect(signalingServerUri, "room-reconnect", "bob", "Bob");
            try {
                assertTrue(alicePeerJoinedNotice.await(10, TimeUnit.SECONDS),
                        "Alice phai thay Bob vao phong sau khi da tu ket noi lai");
            } finally {
                bob.disconnect();
            }

            alice.disconnect();
        } finally {
            serverContextAfterRestart.close();
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static ConfigurableApplicationContext startServerOnPort(int port) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(SignalingServerApplication.class)
                .run("--server.port=" + port);
        int actualPort = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        if (actualPort != port) {
            // Khong nen xay ra (da chi dinh port cu the, khong phai 0) nhung kiem tra
            // cho chac - neu lech thi test se that bai ro rang thay vi treo kho hieu.
            throw new IllegalStateException("Server khoi dong sai cong: mong " + port + ", thuc te " + actualPort);
        }
        return context;
    }
}
