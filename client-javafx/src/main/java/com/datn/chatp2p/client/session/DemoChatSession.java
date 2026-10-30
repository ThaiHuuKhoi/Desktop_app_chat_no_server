package com.datn.chatp2p.client.session;

import com.datn.chatp2p.client.demo.DemoPeerSimulator;
import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.model.PeerVerificationState;
import com.datn.chatp2p.crypto.AesGcmCipher;
import com.datn.chatp2p.crypto.Fingerprint;
import com.datn.chatp2p.crypto.KeyExchangeService;
import com.datn.chatp2p.p2p.LoopbackDataChannel;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;

/**
 * {@link ChatSession} cho che do demo: 1 {@link DemoPeerSimulator} noi qua
 * {@link LoopbackDataChannel}, ma hoa that bang ECDH + AES-GCM - giong het
 * pipeline cu trong {@code RoomController}, chi chuyen ra khoi giao dien.
 *
 * <p>Peer gia lap "vao phong" sau 1 khoang tre ngan de giao dien the hien dung
 * vong doi that (cho -> co nguoi vao -> thiet lap kenh ma hoa).
 */
public final class DemoChatSession implements ChatSession {

    static final String DEMO_PEER_NAME = "Người dùng demo";
    private static final long SIMULATED_CONNECT_DELAY_MILLIS = 900;

    private final String selfPeerId = UUID.randomUUID().toString();
    private final KeyPair myKeyPair = KeyExchangeService.generateKeyPair();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "demo-chat-session");
        thread.setDaemon(true);
        return thread;
    });

    private volatile ChatSessionListener listener;
    private volatile DataChannel myChannel;
    private volatile DemoPeerSimulator demoPeer;
    private volatile SecretKey sharedKey;
    private volatile boolean left;

    @Override
    public String selfPeerId() {
        return selfPeerId;
    }

    @Override
    public void start(ChatSessionListener listener) {
        this.listener = listener;
        scheduler.schedule(this::connectDemoPeer, SIMULATED_CONNECT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void connectDemoPeer() {
        if (left) {
            return;
        }
        LoopbackDataChannel.Pair pair = LoopbackDataChannel.createPair();
        DemoPeerSimulator peer = new DemoPeerSimulator(pair.endpointB(), myKeyPair.getPublic());
        sharedKey = KeyExchangeService.deriveSharedSecret(myKeyPair.getPrivate(), peer.getPublicKey());
        pair.endpointA().onReceive(this::onEncryptedMessageReceived);
        demoPeer = peer;
        myChannel = pair.endpointA();

        listener.onPeerJoined(new SessionPeer(
                peer.getPeerId(),
                DEMO_PEER_NAME,
                Fingerprint.of(myKeyPair.getPublic()),
                Fingerprint.of(peer.getPublicKey()),
                SessionPeer.ConnectionKind.DEMO,
                PeerVerificationState.VERIFIED));
    }

    @Override
    public void sendGroupMessage(ChatMessage message) {
        DataChannel channel = myChannel;
        if (channel == null) {
            // Chua co ai trong phong - giong mesh that: broadcast toi 0 peer.
            return;
        }
        channel.send(AesGcmCipher.encrypt(sharedKey, message.getText().getBytes(StandardCharsets.UTF_8)));
    }

    private void onEncryptedMessageReceived(byte[] encrypted) {
        String text = new String(AesGcmCipher.decrypt(sharedKey, encrypted), StandardCharsets.UTF_8);
        ChatMessage message = new ChatMessage(demoPeer.getPeerId(), text);
        message.markReceived();
        listener.onMessageReceived(message);
    }

    @Override
    public void leave() {
        left = true;
        scheduler.shutdownNow();
        if (myChannel != null) {
            myChannel.close();
        }
        if (demoPeer != null) {
            demoPeer.stop();
        }
    }
}
