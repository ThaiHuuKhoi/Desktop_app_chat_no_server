package com.datn.chatp2p.p2p.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra {@link P2pDataChannel} bang 2 {@link DatagramSocket} that tren
 * localhost (khac cong UDP) - khong dung ice4j, chi xac nhan logic
 * framing/gui/nhan cua rieng lop nay dung, dung nhu goi y kiem thu o
 * Tai-lieu-ky-thuat.md Phan F.5.2 ("2 instance tren cung may, khac cong UDP").
 */
class P2pDataChannelTest {

    private P2pDataChannel channelA;
    private P2pDataChannel channelB;

    @AfterEach
    void tearDown() {
        if (channelA != null) channelA.close();
        if (channelB != null) channelB.close();
    }

    @Test
    void sendsAndReceivesDataBetweenTwoRealUdpSockets() throws Exception {
        DatagramSocket socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());

        InetSocketAddress addressA = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort());
        InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

        channelA = new P2pDataChannel(socketA, addressB);
        channelB = new P2pDataChannel(socketB, addressA);

        CountDownLatch bReceived = new CountDownLatch(1);
        List<byte[]> bInbox = new ArrayList<>();
        channelB.onReceive(data -> {
            bInbox.add(data);
            bReceived.countDown();
        });

        byte[] messageFromA = "xin chao tu A".getBytes(StandardCharsets.UTF_8);
        channelA.send(messageFromA);

        assertTrue(bReceived.await(5, TimeUnit.SECONDS), "B phai nhan duoc du lieu tu A trong 5s");
        assertEquals(1, bInbox.size());
        assertEquals("xin chao tu A", new String(bInbox.get(0), StandardCharsets.UTF_8));
    }

    @Test
    void receiveLoopSurvivesAHandlerThatThrowsOnAMalformedOrTamperedPacket() throws Exception {
        // Ly do co that: handler dang ky qua onReceive() (o RoomSession thuc te
        // la PeerConnection.handleIncoming, giai ma AES-GCM) hoan toan co the nem
        // loi neu 1 goi tin da qua duoc kiem tra framing nhung noi dung ben trong
        // hong/gia mao (sai khoa, du lieu bi sua tren duong truyen, hoac 1 goi UDP
        // la roi vao dung cong). Truoc khi sua, loi do se thoat ra khoi
        // runReceiveLoop() va lam CHET VINH VIEN thread nhan - kenh tro thanh
        // "xac song". Test nay xac nhan: sau 1 handler NEM LOI, kenh van phai
        // nhan duoc goi tin TIEP THEO binh thuong.
        DatagramSocket socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());

        InetSocketAddress addressA = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort());
        InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

        channelA = new P2pDataChannel(socketA, addressB);
        channelB = new P2pDataChannel(socketB, addressA);

        AtomicBoolean firstCall = new AtomicBoolean(true);
        CountDownLatch secondPacketReceived = new CountDownLatch(1);
        List<byte[]> bInbox = new ArrayList<>();
        channelB.onReceive(data -> {
            if (firstCall.compareAndSet(true, false)) {
                throw new RuntimeException("gia lap loi giai ma - goi tin dau tien hong/gia mao");
            }
            bInbox.add(data);
            secondPacketReceived.countDown();
        });

        channelA.send("goi tin dau - se lam handler nem loi".getBytes(StandardCharsets.UTF_8));
        channelA.send("goi tin thu 2 - phai van nhan duoc binh thuong".getBytes(StandardCharsets.UTF_8));

        assertTrue(secondPacketReceived.await(5, TimeUnit.SECONDS),
                "Vong lap nhan phai song sot sau khi handler nem loi o goi tin dau va van nhan duoc goi tin thu 2");
        assertEquals("goi tin thu 2 - phai van nhan duoc binh thuong", new String(bInbox.get(0), StandardCharsets.UTF_8));
    }

    @Test
    void closingChannelStopsReceiveLoopWithoutThrowing() throws Exception {
        DatagramSocket socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        try (DatagramSocket socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            InetSocketAddress addressB = new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort());

            channelA = new P2pDataChannel(socketA, addressB);
            channelA.close();

            // Goi send() sau khi dong phai bao loi ro rang, khong duoc gui ngam vao khoang khong.
            try {
                channelA.send("qua muon".getBytes(StandardCharsets.UTF_8));
                throw new AssertionError("send() sau close() phai nem IllegalStateException");
            } catch (IllegalStateException expected) {
                // dung hanh vi mong doi
            }
        }
    }
}
