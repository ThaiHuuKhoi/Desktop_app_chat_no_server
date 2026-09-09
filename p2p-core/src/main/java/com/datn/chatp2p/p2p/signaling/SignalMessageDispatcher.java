package com.datn.chatp2p.p2p.signaling;

import com.datn.chatp2p.common.signal.SignalMessage;
import com.datn.chatp2p.common.signal.SignalType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Dang ky handler theo {@link SignalType} va phat (dispatch) {@link SignalMessage}
 * toi dung handler - logic dung CHUNG giua {@link WebSocketSignalingClient} (that)
 * va bo gia lap dung cho test (khong o module nay nhung cung goi trong package
 * {@code com.datn.chatp2p.p2p}), tach ra de tranh trung lap 2 noi y het nhau
 * (moi cai dat {@link SignalingClient} truoc day tu viet lai cung 1
 * {@code EnumMap<SignalType, List<Consumer<SignalMessage>>>}).
 *
 * <p>Khong tu no la 1 {@link SignalingClient} - chi la thanh phan noi bo de
 * cac cai dat {@code SignalingClient} dung lai, khong lien quan gi den
 * viec ket noi mang (WebSocket that hay gia lap trong bo nho).
 */
public final class SignalMessageDispatcher {

    private final Map<SignalType, List<Consumer<SignalMessage>>> handlersByType = new EnumMap<>(SignalType.class);

    /** Dang ky {@code handler} de nhan moi {@link SignalMessage} co {@code type} tuong ung sau nay. */
    public void register(SignalType type, Consumer<SignalMessage> handler) {
        handlersByType.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Goi toi tat ca handler da dang ky cho dung {@code message.getType()} - khong lam gi neu type null hoac chua co ai dang ky. */
    public void dispatch(SignalMessage message) {
        if (message.getType() == null) {
            return;
        }
        List<Consumer<SignalMessage>> forType = handlersByType.get(message.getType());
        if (forType == null) {
            return;
        }
        for (Consumer<SignalMessage> handler : forType) {
            handler.accept(message);
        }
    }
}
