package com.datn.chatp2p.p2p.channel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chia manh ban tin lon: ice4j cat bo moi goi UDP lon hon 1500 byte, nen MOI datagram gui ra phai nho,
 * va ben nhan phai ghep lai dung - ke ca khi manh den sai thu tu, trung, hoac thieu.
 */
class P2pDataChannelFragmentationTest {

    private final List<AutoCloseable> toClose = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable closeable : toClose) {
            closeable.close();
        }
    }

    @Test
    void everyDatagramFitsUnderTheIce4jBufferAndBigMessagesRoundTrip() throws Exception {
        DatagramSocket socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket rawReceiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        toClose.add(rawReceiver);
        P2pDataChannel sender = new P2pDataChannel(socketA,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), rawReceiver.getLocalPort()));
        toClose.add(sender::close);

        byte[] big = new byte[20_000];
        new Random(1).nextBytes(big);
        sender.send(big);

        rawReceiver.setSoTimeout(2000);
        int datagrams = 0;
        byte[] buffer = new byte[65_507];
        while (datagrams < 18) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            rawReceiver.receive(packet);
            assertTrue(packet.getLength() <= 1200, "Datagram " + packet.getLength() + " byte se bi ice4j cat");
            datagrams++;
        }
        assertEquals(18, datagrams, "20 000 byte / 1150 byte moi manh = 18 manh");
    }

    @Test
    void twoRealChannelsExchangeSmallAndLargeMessages() throws Exception {
        DatagramSocket socketA = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        DatagramSocket socketB = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        P2pDataChannel a = new P2pDataChannel(socketA,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), socketB.getLocalPort()));
        P2pDataChannel b = new P2pDataChannel(socketB,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), socketA.getLocalPort()));
        toClose.add(a::close);
        toClose.add(b::close);
        LinkedBlockingQueue<byte[]> inbox = new LinkedBlockingQueue<>();
        b.onReceive(inbox::add);

        byte[] small = "xin chao".getBytes();
        byte[] large = new byte[50_000];
        new Random(2).nextBytes(large);
        a.send(small);
        a.send(large);

        assertArrayEquals(small, inbox.poll(3, TimeUnit.SECONDS));
        assertArrayEquals(large, inbox.poll(3, TimeUnit.SECONDS));
    }

    @Test
    void reassemblesOutOfOrderAndDuplicateFragmentsAndIgnoresIncompleteOnes() throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        P2pDataChannel channel = new P2pDataChannel(socket, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9));
        toClose.add(channel::close);

        byte[] message = new byte[5_000];
        new Random(3).nextBytes(message);
        List<byte[]> frames = new ArrayList<>(channel.frames(message));
        assertTrue(frames.size() > 1);
        Collections.reverse(frames);
        frames.add(1, frames.get(0)); // manh trung

        byte[] result = null;
        for (byte[] frame : frames) {
            byte[] complete = channel.accept(frame, frame.length);
            if (complete != null) {
                result = complete;
            }
        }
        assertArrayEquals(message, result);

        List<byte[]> partial = channel.frames(message);
        for (int i = 0; i < partial.size() - 1; i++) {
            assertNull(channel.accept(partial.get(i), partial.get(i).length), "Thieu manh cuoi -> chua co ban tin");
        }
    }

    @Test
    void rejectsMalformedAndOversizedInput() throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        P2pDataChannel channel = new P2pDataChannel(socket, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9));
        toClose.add(channel::close);

        assertThrows(IllegalArgumentException.class, () -> channel.accept(new byte[] {7, 1, 2}, 3));
        byte[] hugeCount = {2, 0, 0, 0, 1, 0, 0, (byte) 0xFF, (byte) 0xFF, 42};
        assertThrows(IllegalArgumentException.class, () -> channel.accept(hugeCount, hugeCount.length));
        assertThrows(IllegalArgumentException.class, () -> channel.send(new byte[P2pDataChannel.MAX_MESSAGE_BYTES + 1]));
        assertNotNull(channel.accept(new byte[] {1, 9}, 2));
    }
}
