package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.signaling.SignalingServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Do THAT kha nang chiu tai cua rieng Tang 1 (signaling) - tach biet khoi
 * gioi han N^2 cua mesh/ICE (Tai-lieu-ky-thuat.md Phan F.3), vi ket noi
 * signaling KHONG can ICE Agent/cong UDP - chi la 1 WebSocket + vai ban tin
 * JSON nho. Cau hoi dang tra loi: "signaling-server chiu duoc bao nhieu
 * NGUOI DUNG cung luc" - khac voi "1 PHONG chiu duoc bao nhieu peer" (gioi
 * han do la cua tang ICE/mesh, khong phai cua tang nay).
 *
 * <p>Mo phong N nguoi dung CUNG LUC ket noi vao 1 phong qua 1
 * signaling-server that (khong gia lap) - moi nguoi tren 1 thread rieng
 * (dung nhu N tien trinh client that ket noi gan nhu dong thoi), xac nhan
 * TAT CA deu ket noi thanh cong VA moi nguoi biet dung tat ca nhung nguoi
 * con lai (khong ai bi "that lac" do race condition - day chinh la phep
 * thu tai voi so luong lon cho fix RoomRegistry.join() da lam o commit
 * truoc, truoc do chi test voi 2 peer).
 */
class WebSocketSignalingClientCapacityTest {

    @Test
    void manyPeersJoiningConcurrentlyAllDiscoverEachOtherCorrectly() throws Exception {
        int peerCount = 30; // du lon de la phep thu tai concurrency that su, van chay nhanh trong 1 unit test

        ConfigurableApplicationContext serverContext = new SpringApplicationBuilder(SignalingServerApplication.class)
                .run("--server.port=0");
        int port = ((ServletWebServerApplicationContext) serverContext).getWebServer().getPort();
        String signalingServerUri = "ws://localhost:" + port;

        List<WebSocketSignalingClient> clients = new CopyOnWriteArrayList<>();
        ConcurrentHashMap<String, Set<String>> knownPeersByClient = new ConcurrentHashMap<>();
        CountDownLatch allJoined = new CountDownLatch(peerCount);
        ExecutorService executor = Executors.newFixedThreadPool(peerCount);

        long startNanos = System.nanoTime();
        try {
            for (int i = 0; i < peerCount; i++) {
                String peerId = "peer-" + i;
                executor.submit(() -> {
                    WebSocketSignalingClient client = new WebSocketSignalingClient();
                    Set<String> known = ConcurrentHashMap.newKeySet();
                    knownPeersByClient.put(peerId, known);

                    client.onPeerList(message -> {
                        for (var info : message.getPeers()) {
                            known.add(info.getPeerId());
                        }
                    });
                    client.onPeerJoined(message -> known.add(message.getFromPeerId()));

                    client.connect(signalingServerUri, "room-capacity", peerId, "Peer-" + peerId);
                    clients.add(client);
                    allJoined.countDown();
                });
            }

            assertTrue(allJoined.await(30, TimeUnit.SECONDS),
                    "Ca " + peerCount + " peer phai ket noi + JOIN xong trong 30s");
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            System.out.println(peerCount + " peer join dong thoi qua signaling-server that mat " + elapsedMillis + "ms");

            // Cho cac thong bao PEER_JOINED con dang bay (bat dong bo qua WebSocket that,
            // co the toi sau khi allJoined da dem xong vi connect() chi cho JOIN cua CHINH
            // minh, khong cho thong bao ve nguoi khac) kip toi truoc khi kiem tra.
            Thread.sleep(2000);

            Set<String> allPeerIds = IntStream.range(0, peerCount)
                    .mapToObj(i -> "peer-" + i)
                    .collect(Collectors.toSet());

            for (int i = 0; i < peerCount; i++) {
                String peerId = "peer-" + i;
                Set<String> expected = allPeerIds.stream()
                        .filter(id -> !id.equals(peerId))
                        .collect(Collectors.toSet());
                assertEquals(expected, knownPeersByClient.get(peerId),
                        peerId + " phai biet dung tat ca " + (peerCount - 1) + " peer con lai, khong thieu/du");
            }
        } finally {
            executor.shutdown();
            for (WebSocketSignalingClient client : clients) {
                client.disconnect();
            }
            serverContext.close();
        }
    }
}
