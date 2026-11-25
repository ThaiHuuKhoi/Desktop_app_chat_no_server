package com.datn.chatp2p.client.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingEditsTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final PendingEdits pending = new PendingEdits(now::get);

    @Test
    void parkedEditIsTakenOnceForTheSameAuthorMessageAndConversation() {
        pending.park("an", "m1", false, "noi dung moi", false);

        assertTrue(pending.take("minh", "m1", false).isEmpty(), "Tac gia khac khong lay duoc lenh cua An");
        assertTrue(pending.take("an", "m1", true).isEmpty(), "Lenh trong tin nhom khong ap sang tin rieng");
        PendingEdits.Edit edit = pending.take("an", "m1", false).orElseThrow();
        assertEquals("noi dung moi", edit.text());
        assertFalse(edit.deleted());
        assertTrue(pending.take("an", "m1", false).isEmpty(), "Lay roi thi het");
    }

    @Test
    void deleteWinsOverLaterEdit() {
        pending.park("an", "m1", false, null, true);
        pending.park("an", "m1", false, "sua sau khi xoa", false);

        assertTrue(pending.take("an", "m1", false).orElseThrow().deleted());
    }

    @Test
    void laterEditReplacesEarlierEdit() {
        pending.park("an", "m1", false, "lan 1", false);
        pending.park("an", "m1", false, "lan 2", false);

        assertEquals("lan 2", pending.take("an", "m1", false).orElseThrow().text());
    }

    @Test
    void expiredEditIsDropped() {
        pending.park("an", "m1", false, null, true);
        now.addAndGet(PendingEdits.TTL_MILLIS + 1);

        assertTrue(pending.take("an", "m1", false).isEmpty());
    }

    @Test
    void floodingIsCappedPerPeerAndDoesNotAffectOthers() {
        pending.park("binh", "b1", false, null, true);
        for (int i = 0; i < PendingEdits.MAX_PER_PEER + 500; i++) {
            pending.park("minh", "rac-" + i, false, null, true);
        }

        assertEquals(PendingEdits.MAX_PER_PEER, pending.size("minh"));
        assertTrue(pending.take("minh", "rac-0", false).isEmpty(), "Muc cu nhat bi bo khi day");
        assertTrue(pending.take("binh", "b1", false).isPresent(), "Nguoi khac khong bi day ra");
    }

    @Test
    void peerLeavingDropsTheirEdits() {
        pending.park("an", "m1", false, null, true);
        pending.forgetPeer("an");

        assertTrue(pending.take("an", "m1", false).isEmpty());
    }
}
