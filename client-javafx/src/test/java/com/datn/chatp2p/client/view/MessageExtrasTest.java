package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.model.ChatMessage;
import com.datn.chatp2p.common.protocol.MessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cam xuc, "da xem", tra loi, nhan dien file anh. */
class MessageExtrasTest {

    @Test
    void reactionsToggleCountAndIgnoreUnknownEmoji() {
        ReactionStore store = new ReactionStore();
        assertTrue(store.apply("m1", "a", "👍", true));
        assertTrue(store.apply("m1", "b", "👍", true));
        assertTrue(store.apply("m1", "a", "😂", true));
        assertFalse(store.apply("m1", "a", "👍", true), "Tha lai cung cam xuc khong doi gi");
        assertFalse(store.apply("m1", "a", "<script>", true), "Chi nhan cam xuc trong bo co dinh");

        assertEquals(Map.of("👍", 2, "😂", 1), store.counts("m1"));
        assertEquals(List.of("👍", "😂"), List.copyOf(store.counts("m1").keySet()), "Thu tu co dinh");
        assertTrue(store.hasReacted("m1", "a", "👍"));

        assertTrue(store.apply("m1", "a", "👍", false));
        assertTrue(store.peerLeft("b"));
        assertEquals(Map.of("😂", 1), store.counts("m1"));
    }

    @Test
    void readImpliesDeliveredAndShowsLabels() {
        DeliveryStatuses statuses = new DeliveryStatuses();
        statuses.sent("m", List.of("a", "b"));
        assertTrue(statuses.read("m", "a"));
        assertEquals("Đã xem 1/2", statuses.status("m").label());
        assertEquals(1, statuses.status("m").delivered(), "Da xem thi tinh luon la da nhan");

        statuses.read("m", "b");
        assertEquals("Tất cả đã xem", statuses.status("m").label());
        assertFalse(statuses.read("m", "stranger"));

        statuses.sent("dm", List.of("a"));
        statuses.read("dm", "a");
        assertEquals("Đã xem", statuses.status("dm").label());
    }

    @Test
    void replyIdTravelsInPayloadAndOldPayloadsStillParse() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ChatMessage reply = ChatMessage.reply("me", "dong y", "m-goc");
        String encoded = json.writeValueAsString(new MessagePayload(reply.getId(), "me", "dong y",
                reply.getTimeSent(), reply.getReplyToId()));

        assertEquals("m-goc", json.readValue(encoded, MessagePayload.class).replyToId());
        // Ban tin tu client chua co truong replyToId
        MessagePayload old = json.readValue("{\"id\":\"x\",\"authorId\":\"a\",\"text\":\"hi\",\"timeSent\":1}",
                MessagePayload.class);
        assertNull(old.replyToId());
    }

    @Test
    void snippetIsOneLineAndShort() {
        assertEquals("dong 1 dong 2", MessageTexts.snippet("dong 1\n\n  dong 2  "));
        String longText = "a".repeat(200);
        assertEquals(81, MessageTexts.snippet(longText).length());
    }

    @Test
    void imageDetectionByExtension() {
        assertTrue(MessageTexts.isImage("anh.PNG"));
        assertTrue(MessageTexts.isImage("photo.jpeg"));
        assertFalse(MessageTexts.isImage("virus.exe"));
        assertFalse(MessageTexts.isImage("png"));
        assertFalse(MessageTexts.isImage(null));
    }
}
