package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.MessageTranscriptPayload.HistoryMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupHistoryTest {

    private static HistoryMessage msg(String id, long time, String text) {
        return new HistoryMessage(id, "author", "Tên", text, time);
    }

    @Test
    void deduplicatesByIdAndKeepsChronologicalOrder() {
        GroupHistory history = new GroupHistory();
        assertTrue(history.add(msg("b", 20, "hai")));
        assertTrue(history.add(msg("a", 10, "một")));
        assertTrue(history.add(msg("c", 30, "ba")));
        assertFalse(history.add(msg("a", 10, "một")), "Cung id (vd 2 nguoi cung gui lich su) chi giu 1");

        assertEquals(List.of("a", "b", "c"), history.snapshot().stream().map(HistoryMessage::id).toList());
    }

    @Test
    void keepsOnlyMostRecentMessages() {
        GroupHistory history = new GroupHistory();
        for (int i = 0; i < GroupHistory.MAX_MESSAGES + 5; i++) {
            history.add(msg("m" + i, i, "x"));
        }
        List<HistoryMessage> snapshot = history.snapshot();
        assertEquals(GroupHistory.MAX_MESSAGES, snapshot.size());
        assertEquals("m5", snapshot.get(0).id());
        assertFalse(history.contains("m0"));
    }

    @Test
    void rejectsInvalidOrOversizedMessages() {
        GroupHistory history = new GroupHistory();
        assertFalse(history.add(new HistoryMessage(null, "a", "n", "x", 1)));
        assertFalse(history.add(new HistoryMessage("id", null, "n", "x", 1)));
        assertFalse(history.add(msg("big", 1, "x".repeat(GroupHistory.MAX_TEXT_CHARS + 1))));
    }

    @Test
    void batchesStayWithinBudget() {
        List<HistoryMessage> messages = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            messages.add(msg("m" + i, i, "y".repeat(500)));
        }
        messages.add(msg("long", 99, "z".repeat(GroupHistory.MAX_TEXT_CHARS)));

        List<List<HistoryMessage>> batches = GroupHistory.batches(messages);
        assertEquals(51, batches.stream().mapToInt(List::size).sum(), "Khong mat tin nao khi chia goi");
        for (List<HistoryMessage> batch : batches) {
            int chars = batch.stream().mapToInt(m -> m.text().length() + m.authorName().length()).sum();
            assertTrue(batch.size() == 1 || chars <= GroupHistory.BATCH_CHAR_BUDGET);
        }
        assertEquals(List.of(), GroupHistory.batches(List.of()));
    }
}
