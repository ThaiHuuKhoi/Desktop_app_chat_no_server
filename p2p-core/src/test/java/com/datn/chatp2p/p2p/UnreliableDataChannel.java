package com.datn.chatp2p.p2p;

import com.datn.chatp2p.common.channel.DataChannel;

import java.util.function.Consumer;

/**
 * Boc 1 {@link DataChannel} (thuong la 1 dau cua {@link LoopbackDataChannel.Pair}) va ap
 * {@link NetworkImpairment} len chieu GUI - mo phong UDP that: mat / nhan doi / dao goi.
 * Boc ca 2 dau de suy giam ca 2 chieu.
 */
public final class UnreliableDataChannel implements DataChannel {

    private final DataChannel inner;
    private final NetworkImpairment impairment;

    public UnreliableDataChannel(DataChannel inner, NetworkImpairment impairment) {
        this.inner = inner;
        this.impairment = impairment;
    }

    @Override
    public void send(byte[] data) {
        byte[] copy = data.clone();
        impairment.deliver(copy, bytes -> {
            try {
                inner.send(bytes);
            } catch (IllegalStateException closed) {
                // kenh da dong trong luc goi dang "bay"
            }
        });
    }

    @Override
    public void onReceive(Consumer<byte[]> handler) {
        inner.onReceive(handler);
    }

    @Override
    public void close() {
        inner.close();
    }
}
