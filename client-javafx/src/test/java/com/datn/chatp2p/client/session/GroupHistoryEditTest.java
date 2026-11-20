package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.MessageTranscriptPayload.HistoryMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sua / xoa tin phai cap nhat lich su chia se cho nguoi vao sau - va chi tac gia moi lam duoc. */
class GroupHistoryEditTest {

    @Test
    void onlyTheAuthorCanEdit() {
        GroupHistory history = new GroupHistory();
        history.add(new HistoryMessage("m1", "alice", "Alice", "ban cu", 1));

        assertFalse(history.edit("m1", "mallory", "bi sua trom"), "Nguoi khac khong sua duoc");
        assertTrue(history.edit("m1", "alice", "ban moi"));

        assertEquals("ban moi", history.snapshot().get(0).text());
    }

    @Test
    void deletedMessageIsGoneAndNotReAddedFromSomeoneElsesHistory() {
        GroupHistory history = new GroupHistory();
        history.add(new HistoryMessage("m1", "alice", "Alice", "se bi xoa", 1));

        assertFalse(history.remove("m1", "mallory"));
        assertTrue(history.remove("m1", "alice"));
        assertTrue(history.snapshot().isEmpty());

        // Ban cu nam trong lich su do nguoi khac gui toi -> khong duoc hien lai
        assertFalse(history.add(new HistoryMessage("m1", "alice", "Alice", "se bi xoa", 1)));
    }
}
