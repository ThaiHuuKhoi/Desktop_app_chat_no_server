package com.datn.chatp2p.p2p;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tang 5, kha nang mo rong theo 1 truc KHAC voi cac test da co: khong phai
 * "1 phong co bao nhieu peer CUNG LUC" (da co RoomSessionThreePeerMeshTest,
 * RoomSessionEightPeerMeshScalabilityTest @Disabled) ma la "1 tien trinh
 * client trai qua BAO NHIEU vong doi phong (tao - dung - roi) LIEN TIEP theo
 * THOI GIAN co bi ro ri gi tich luy dan khong" - kich ban thuc te: 1 nguoi
 * dung mo app, vao/roi nhieu phong chat khac nhau trong 1 phien lam viec dai
 * (khong tat app giua chung).
 *
 * <p>Moi RoomSession that tao ra it nhat 1 {@code P2pDataChannel} that (socket
 * UDP that + 1 thread nhan rieng ten "p2p-datachannel-receive", xem
 * P2pDataChannel constructor) cho MOI peer da ket noi. Neu {@link RoomSession#leave()}
 * hoac {@code PeerConnection#close()}/{@code P2pDataChannel#close()} co so sot
 * o bat ky buoc nao, thread nay se KHONG BAO GIO dung han (vong lap
 * {@code runReceiveLoop} van cho socket.receive() vinh vien) - tich luy dan
 * qua tung phong da roi, cuoi cung can kiet thread cua tien trinh sau du
 * nguoi dung chi dung 1 phong tai 1 thoi diem.
 */
class RoomSessionRepeatedLifecycleScalabilityTest {

    private static final int ROOM_LIFECYCLES = 10;

    @Test
    void repeatedlyJoiningAndLeavingManyRoomsInSequenceDoesNotLeakDataChannelReceiveThreads() throws Exception {
        for (int i = 0; i < ROOM_LIFECYCLES; i++) {
            runOneRoomLifecycle("room-lifecycle-" + i);
        }

        // Cho cac thread vua bi shutdownNow() thuc su ket thuc (shutdownNow chi
        // NGAT tin hieu interrupt, khong dam bao thread dung NGAY lap tuc).
        Thread.sleep(500);

        long leakedReceiveThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("p2p-datachannel-receive"))
                .count();

        assertEquals(0, leakedReceiveThreads,
                "Sau " + ROOM_LIFECYCLES + " vong doi phong lien tiep (tao - ket noi - roi), "
                        + "khong duoc con thread nhan P2pDataChannel nao con song - moi thread con "
                        + "song la 1 socket UDP + 1 vong lap receive() chay vinh vien bi ro ri tu "
                        + "1 phong DA ROI truoc do");
    }

    private void runOneRoomLifecycle(String roomId) throws InterruptedException {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();
        RoomSession a = new RoomSession(roomId, "a", "A", new LoopbackSignalingClient(hub), List.of());
        RoomSession b = new RoomSession(roomId, "b", "B", new LoopbackSignalingClient(hub), List.of());

        CountDownLatch bSeesA = new CountDownLatch(1);
        b.onPeerJoined(connection -> bSeesA.countDown());

        a.join("ws://fake-signaling-server/ws");
        b.join("ws://fake-signaling-server/ws");

        assertTrue(bSeesA.await(10, TimeUnit.SECONDS),
                "Phong " + roomId + " phai ket noi ICE + ECDH xong that trong 10s truoc khi roi");

        a.leave();
        b.leave();
    }
}
