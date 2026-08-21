package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoopbackDataChannelTest {

    @Test
    void messagesSentOnOneEndpointArriveOnTheOther() throws InterruptedException {
        LoopbackDataChannel.Pair pair = LoopbackDataChannel.createPair();
        DataChannel you = pair.endpointA();
        DataChannel demoPeer = pair.endpointB();

        BlockingQueue<byte[]> receivedByDemoPeer = new ArrayBlockingQueue<>(1);
        BlockingQueue<byte[]> receivedByYou = new ArrayBlockingQueue<>(1);
        demoPeer.onReceive(receivedByDemoPeer::add);
        you.onReceive(receivedByYou::add);

        you.send("Xin chao!".getBytes(StandardCharsets.UTF_8));
        byte[] atDemoPeer = receivedByDemoPeer.poll(2, TimeUnit.SECONDS);
        assertNotNull(atDemoPeer);
        assertEquals("Xin chao!", new String(atDemoPeer, StandardCharsets.UTF_8));

        demoPeer.send("Chao ban!".getBytes(StandardCharsets.UTF_8));
        byte[] atYou = receivedByYou.poll(2, TimeUnit.SECONDS);
        assertNotNull(atYou);
        assertEquals("Chao ban!", new String(atYou, StandardCharsets.UTF_8));

        you.close();
        demoPeer.close();
    }
}
