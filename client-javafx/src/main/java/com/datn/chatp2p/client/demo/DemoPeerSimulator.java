package com.datn.chatp2p.client.demo;

import com.datn.chatp2p.common.channel.DataChannel;
import com.datn.chatp2p.crypto.AesGcmCipher;
import com.datn.chatp2p.crypto.KeyExchangeService;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;

/**
 * "Doi phuong" gia lap chay noi bo trong cung tien trinh, dung de demo giao
 * dien + pipeline ma hoa ma khong can cho ket noi mang that (xem
 * {@code p2p-core}'s {@code LoopbackDataChannel} va TODO cua P2pDataChannel).
 *
 * <p>Nhan tin nhan da ma hoa AES-GCM tu phia "ban", giai ma that, roi sau mot
 * khoang tre ngan tra loi mot cau tra loi tu dong, cung duoc ma hoa that -
 * chung minh toan bo pipeline (ECDH + AES-GCM + DataChannel) hoat dong dung,
 * du day khong phai la mot ket noi P2P qua mang thuc.
 */
public final class DemoPeerSimulator {

    private static final List<String> CANNED_REPLIES = List.of(
            "Đã nhận tin nhắn của bạn qua kênh mã hoá!",
            "Xin chào, mình là peer mô phỏng (chưa qua mạng thật).",
            "Nội dung này được giải mã bằng AES-GCM sau khi trao khoá ECDH.",
            "Bản tin đã tới nơi an toàn."
    );

    private final String peerId = UUID.randomUUID().toString();
    private final KeyPair keyPair = KeyExchangeService.generateKeyPair();
    private final SecretKey sharedKey;
    private final DataChannel channel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "demo-peer-simulator");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger replyIndex = new AtomicInteger();

    public DemoPeerSimulator(DataChannel channel, PublicKey yourPublicKey) {
        this.channel = channel;
        this.sharedKey = KeyExchangeService.deriveSharedSecret(keyPair.getPrivate(), yourPublicKey);
        this.channel.onReceive(this::onMessageReceived);
    }

    public String getPeerId() {
        return peerId;
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    private void onMessageReceived(byte[] encrypted) {
        // Giai ma de "doc duoc" tin nhan - chi de minh hoa; peer mo phong khong
        // luu lai noi dung, dung ngay xong roi bo qua (dung tinh than ephemeral).
        AesGcmCipher.decrypt(sharedKey, encrypted);

        scheduler.schedule(this::sendReply, 1, TimeUnit.SECONDS);
    }

    private void sendReply() {
        String reply = CANNED_REPLIES.get(replyIndex.getAndIncrement() % CANNED_REPLIES.size());
        byte[] encrypted = AesGcmCipher.encrypt(sharedKey, reply.getBytes(StandardCharsets.UTF_8));
        channel.send(encrypted);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
