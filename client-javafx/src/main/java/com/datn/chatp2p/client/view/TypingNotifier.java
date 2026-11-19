package com.datn.chatp2p.client.view;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Phia GUI cua "dang go": quyet dinh khi nao bao {@code typing = true/false} de khong gui moi
 * phim bam. Thuan logic (dong ho tiem vao) de test khong can JavaFX.
 *
 * <ul>
 *   <li>Go phim dau tien -&gt; gui {@code true}; van go tiep thi nhac lai {@code true} moi
 *       {@link #KEEPALIVE_MILLIS} (ben nhan het han sau {@link TypingTracker#EXPIRY_MILLIS}, va
 *       goi tin co the mat).</li>
 *   <li>Ngung go {@link #IDLE_MILLIS}, xoa het chu, gui tin, hoac doi tab -&gt; gui {@code false}.</li>
 * </ul>
 * Khong thread-safe - chi goi tu JavaFX thread.
 */
final class TypingNotifier {

    static final long IDLE_MILLIS = 3_000;
    static final long KEEPALIVE_MILLIS = 2_500;

    /** Noi gui tin hieu that ({@code channel} null = nhom, khac null = peerId cua tab tin nhan rieng). */
    interface Sink {
        void send(String channel, boolean typing);
    }

    private final Sink sink;
    private final LongSupplier clock;

    private boolean typing;
    private String channel;
    private long lastInputAt;
    private long lastAnnouncedAt;

    TypingNotifier(Sink sink, LongSupplier clock) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Noi dung o nhap thay doi. {@code textBlank} = o nhap trong (xoa het hoac vua gui). */
    void onTextChanged(String channel, boolean textBlank) {
        if (textBlank) {
            stop();
            return;
        }
        long now = clock.getAsLong();
        if (typing && !Objects.equals(this.channel, channel)) {
            stop();
        }
        this.channel = channel;
        lastInputAt = now;
        if (!typing || now - lastAnnouncedAt >= KEEPALIVE_MILLIS) {
            typing = true;
            lastAnnouncedAt = now;
            sink.send(channel, true);
        }
    }

    /** Goi dinh ky (~1 s): het {@link #IDLE_MILLIS} khong go thi bao ngung. */
    void tick() {
        if (typing && clock.getAsLong() - lastInputAt >= IDLE_MILLIS) {
            stop();
        }
    }

    /** Bao ngung go ngay (gui tin, doi tab, roi phong). */
    void stop() {
        if (typing) {
            typing = false;
            sink.send(channel, false);
        }
    }

    boolean isTyping() {
        return typing;
    }
}
