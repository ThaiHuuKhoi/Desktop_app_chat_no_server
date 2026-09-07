package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tang 5 (RoomSession - quan ly mesh), chiu loi: kiem tra xem 1 callback
 * "ICE da ket noi xong" (onIceConnected) TOI SAU KHI leave() da chay xong co
 * lam "hoi sinh" 1 PeerConnection ma khong ai con don dep nua hay khong.
 *
 * <p>onIceConnected() CHAY TREN THREAD RIENG cua ice4j, hoan toan khong dong
 * bo voi thread goi leave() - trong thuc te, ICE hoan tat qua mang that (RTT
 * that su), trong khi leave() chi don dep bo nho trong 1 lan goi dong bo. Vi
 * vay hoan toan hop ly de callback nay "toi tre", ngay SAU KHI leave() da
 * clear() xong peers/pendingEstablishers va ngat signaling.
 *
 * <p>Test nay mo phong DUNG truong hop don gian/dut khoat nhat cua lop loi
 * nay bang cach GOI THANG onIceConnected() (da doi tam nhin sang goi-rieng
 * chi de phuc vu test nay) NGAY SAU leave() - khong can dan dong 1 kich ban
 * ICE that voi timing khong chac chan (se flaky). Day la truong hop CHAC
 * CHAN se xay ra neu callback ICE that tre hon leave(), nen chung minh duoc
 * bang cach nay la du, khong can mo phong ca dai timing co the.
 */
class RoomSessionLateIceCallbackAfterLeaveTest {

    @Test
    void iceConnectedCallbackArrivingAfterLeaveDoesNotResurrectAPeerConnection() {
        LoopbackSignalingClient.Hub hub = new LoopbackSignalingClient.Hub();
        RoomSession self = new RoomSession(
                "room-late-ice", "self", "Self", new LoopbackSignalingClient(hub), List.of());
        self.join("ws://fake-signaling-server/ws");

        // Nguoi dung chu dong roi phong TRUOC KHI callback ICE (gia lap) toi.
        self.leave();

        // Mo phong callback ICE "toi tre" - dung 1 dau LoopbackDataChannel bat
        // ky lam channel (khong quan trong noi dung, chi can mot DataChannel
        // hop le de PeerConnection duoc tao ra).
        DataChannel lateChannel = LoopbackDataChannel.createPair().endpointA();
        self.onIceConnected("late-peer", "LateUser", lateChannel);

        // DIEM MAU CHOT: sau khi da leave() xong, RoomSession khong duoc
        // "hoi sinh" bat ky PeerConnection nao nua - neu getPeerConnection tra
        // ve khac null o day, tuc la 1 PeerConnection (kem socket/thread retry
        // ECDH cua no) vua bi ro ri ngay SAU KHI tuong da roi phong sach se,
        // va se KHONG BAO GIO duoc dong vi leave() da chay va se khong chay lai.
        assertNull(self.getPeerConnection("late-peer"),
                "Callback ICE toi SAU leave() khong duoc phep them PeerConnection moi vao phong da roi");
    }
}
