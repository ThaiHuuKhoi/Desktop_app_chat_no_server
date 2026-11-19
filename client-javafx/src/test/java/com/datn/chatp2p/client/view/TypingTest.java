package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final List<String> sent = new ArrayList<>();
    private final TypingNotifier notifier = new TypingNotifier(
            (channel, typing) -> sent.add((channel == null ? "group" : channel) + ":" + typing), now::get);

    @Test
    void sendsStartOnceThenKeepaliveNotEveryKeystroke() {
        notifier.onTextChanged(null, false);
        now.addAndGet(500);
        notifier.onTextChanged(null, false);
        now.addAndGet(500);
        notifier.onTextChanged(null, false);
        assertEquals(List.of("group:true"), sent, "Khong gui moi phim bam");

        now.addAndGet(TypingNotifier.KEEPALIVE_MILLIS);
        notifier.onTextChanged(null, false);
        assertEquals(List.of("group:true", "group:true"), sent, "Van go lau thi nhac lai de ben nhan khong het han");
    }

    @Test
    void sendsStopAfterIdleOrWhenTextCleared() {
        notifier.onTextChanged(null, false);
        now.addAndGet(TypingNotifier.IDLE_MILLIS - 1);
        notifier.tick();
        assertTrue(notifier.isTyping());
        now.addAndGet(1);
        notifier.tick();
        assertEquals(List.of("group:true", "group:false"), sent);

        notifier.onTextChanged(null, false);
        notifier.onTextChanged(null, true); // xoa het / vua gui tin
        assertEquals(List.of("group:true", "group:false", "group:true", "group:false"), sent);
        notifier.stop();
        assertEquals(4, sent.size(), "stop() khi da ngung thi khong gui them");
    }

    @Test
    void switchingChannelStopsPreviousChannelFirst() {
        notifier.onTextChanged(null, false);
        notifier.onTextChanged("bob", false);
        assertEquals(List.of("group:true", "group:false", "bob:true"), sent);
    }

    @Test
    void trackerExpiresAndSeparatesGroupFromDirect() {
        TypingTracker tracker = new TypingTracker(now::get);
        tracker.update("an", false, true);
        tracker.update("binh", true, true);
        assertEquals(List.of("an"), tracker.groupTypers());
        assertTrue(tracker.isTypingDirect("binh"));
        assertFalse(tracker.isTypingDirect("an"));

        now.addAndGet(TypingTracker.EXPIRY_MILLIS);
        assertEquals(List.of(), tracker.groupTypers(), "Mat goi 'ngung go' thi van tu het han");
        assertFalse(tracker.isTypingDirect("binh"));
    }

    @Test
    void trackerClearsOnMessageAndPeerLeave() {
        TypingTracker tracker = new TypingTracker(now::get);
        tracker.update("an", false, true);
        tracker.update("binh", false, true);
        tracker.clear("an", false);
        tracker.removePeer("binh");
        assertEquals(List.of(), tracker.groupTypers());
    }

    @Test
    void describesTypersInVietnamese() {
        assertEquals("", TypingTracker.describe(List.of()));
        assertEquals("An đang gõ…", TypingTracker.describe(List.of("An")));
        assertEquals("An và Bình đang gõ…", TypingTracker.describe(List.of("An", "Bình")));
        assertEquals("An, Bình và 2 người khác đang gõ…", TypingTracker.describe(List.of("An", "Bình", "C", "D")));
    }
}
