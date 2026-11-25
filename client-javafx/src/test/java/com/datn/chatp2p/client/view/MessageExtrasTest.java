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
    void replyQuoteHidesTextOfDeletedOriginal() {
        // Loi phat hien khi test 2 may 25/11: xoa tin goc nhung khung trich dan cua tin tra loi van hien noi dung.
        assertEquals(MessageTexts.DELETED_QUOTE, MessageTexts.quoteSnippet("bi mat can xoa", true));
        assertFalse(MessageTexts.quoteSnippet("bi mat can xoa", true).contains("bi mat"));
        assertEquals("noi dung **dam**".replace("**", ""), MessageTexts.quoteSnippet("noi dung **dam**", false));
        assertEquals(MessageTexts.MISSING_QUOTE, MessageTexts.quoteSnippet(null, false));
    }

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

    @Test
    void snippetDropsSimpleMarkdownMarkers() {
        assertEquals("Mất 1% gói mà vẫn lên được 18 MB/s.", MessageTexts.snippet("Mất 1% gói mà vẫn lên được **18 MB/s**."));
        assertEquals("chạy lệnh mvn test nhé", MessageTexts.snippet("chạy lệnh `mvn test` nhé"));
        assertEquals("rất quan trọng, bỏ cái này", MessageTexts.snippet("*rất* quan trọng, ~~bỏ~~ cái này"));
        assertEquals("2 * 3 = 6", MessageTexts.snippet("2 * 3 = 6"), "dau * don le giu nguyen");
    }

    @Test
    void displayEmojiDropsVariationSelector() {
        assertEquals("❤", MessageTexts.displayEmoji("❤️"));
        assertEquals("👍", MessageTexts.displayEmoji("👍"));
    }

    @Test
    void everyAllowedReactionHasAColorImage() {
        assertEquals("2764.png", EmojiImages.fileName("❤️"));
        assertEquals("1f44d.png", EmojiImages.fileName("👍"));
        for (String emoji : com.datn.chatp2p.common.protocol.ReactionPayload.ALLOWED) {
            assertTrue(EmojiImages.class.getResource("/emoji/" + EmojiImages.fileName(emoji)) != null,
                    "Thieu anh mau cho cam xuc " + emoji);
        }
    }

    @Test
    void reactorNamesReadNaturally() {
        assertEquals("", MessageTexts.joinNames(java.util.List.of()));
        assertEquals("Bạn", MessageTexts.joinNames(java.util.List.of("Bạn")));
        assertEquals("Bạn và Lan", MessageTexts.joinNames(java.util.List.of("Bạn", "Lan")));
        assertEquals("Bạn, Lan và Tuấn", MessageTexts.joinNames(java.util.List.of("Bạn", "Lan", "Tuấn")));
        assertEquals("A, B, C và D", MessageTexts.joinNames(java.util.List.of("A", "B", "C", "D")));
        assertEquals("A, B, C và 3 người khác",
                MessageTexts.joinNames(java.util.List.of("A", "B", "C", "D", "E", "F")));
    }
}
