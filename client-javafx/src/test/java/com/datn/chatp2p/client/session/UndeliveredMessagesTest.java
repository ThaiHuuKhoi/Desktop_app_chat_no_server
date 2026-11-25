package com.datn.chatp2p.client.session;

import com.datn.chatp2p.common.protocol.EnvelopeNamespace;
import com.datn.chatp2p.common.protocol.MessagePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tin chua toi noi duoc gui lai khi dung nguoi do ket noi lai (loi phat hien khi test 2 may 25/11: tin gui luc
 * mat Wi-Fi hien "Chua toi noi" va khong bao gio duoc gui lai).
 */
class UndeliveredMessagesTest {

    private static final String HUY_KEY = "AAAA BBBB CCCC";
    private static final String IMPOSTOR_KEY = "DDDD EEEE FFFF";

    @Test
    void unacknowledgedMessagesAreResentInOrderToTheSameIdentity() {
        UndeliveredMessages store = new UndeliveredMessages();
        store.remember("huy", null, payload("m1"), HUY_KEY);
        store.remember("huy", EnvelopeNamespace.DIRECT_MESSAGE, payload("m2"), HUY_KEY);
        store.remember("huy", null, payload("m3"), HUY_KEY);
        store.acknowledge("huy", "m2");

        List<UndeliveredMessages.Entry> resend = store.forReconnected("huy", HUY_KEY);

        assertEquals(List.of("m1", "m3"), resend.stream().map(entry -> entry.payload().id()).toList());
        assertEquals(2, store.count("huy"), "Van giu toi khi co ACK - lan gui lai co the cung mat");
        store.acknowledge("huy", "m1");
        store.acknowledge("huy", "m3");
        assertEquals(0, store.count("huy"));
    }

    @Test
    void neverResendToADifferentIdentityUsingTheSamePeerId() {
        UndeliveredMessages store = new UndeliveredMessages();
        store.remember("huy", EnvelopeNamespace.DIRECT_MESSAGE, payload("bi-mat"), HUY_KEY);

        assertTrue(store.forReconnected("huy", IMPOSTOR_KEY).isEmpty(),
                "Ke khac vao lai bang peerId cua Huy khong duoc nhan tin rieng gui cho Huy");
        assertTrue(store.forReconnected("huy", HUY_KEY).isEmpty(),
                "Tin da bi bo khi gap danh tinh khac, khong giu lai de gui sau");
    }

    @Test
    void messagesSentBeforeIdentityWasVerifiedAreNeverResent() {
        UndeliveredMessages store = new UndeliveredMessages();
        store.remember("huy", null, payload("m1"), null);

        assertTrue(store.forReconnected("huy", HUY_KEY).isEmpty());
        assertTrue(store.forReconnected("huy", null).isEmpty());
    }

    @Test
    void failureIsReportedOnlyOnceAndResetWhenUserResends() {
        UndeliveredMessages store = new UndeliveredMessages();
        store.remember("huy", null, payload("m1"), HUY_KEY, 1_000L);

        assertEquals(1, store.pending("huy").size(), "pending() khong xoa tin");
        assertEquals(1_000L, store.pending("huy").get(0).firstSentAtMillis());
        assertTrue(store.contains("huy", "m1"));
        assertTrue(store.markFailureReported("huy", "m1"), "Lan dau phai bao giao dien");
        assertFalse(store.markFailureReported("huy", "m1"), "Chi bao 1 lan du van tiep tuc gui lai");

        store.remember("huy", null, payload("m1"), HUY_KEY, 9_000L); // nguoi dung bam "Gui lai"
        assertEquals(9_000L, store.pending("huy").get(0).firstSentAtMillis());
        assertTrue(store.markFailureReported("huy", "m1"), "Gui lai tay thi dem lai tu dau");

        store.acknowledge("huy", "m1");
        assertFalse(store.contains("huy", "m1"));
        assertFalse(store.markFailureReported("huy", "m1"), "Da co ACK thi khong bao that bai");
    }

    @Test
    void storageIsBoundedPerPeerAndCanBeForgotten() {
        UndeliveredMessages store = new UndeliveredMessages();
        for (int i = 0; i < UndeliveredMessages.MAX_PER_PEER + 50; i++) {
            store.remember("huy", null, payload("m" + i), HUY_KEY);
        }
        assertEquals(UndeliveredMessages.MAX_PER_PEER, store.count("huy"));
        assertEquals("m50", store.forReconnected("huy", HUY_KEY).get(0).payload().id(), "Bo tin cu nhat");

        store.forget("huy");
        assertEquals(0, store.count("huy"));
    }

    private static MessagePayload payload(String id) {
        return new MessagePayload(id, "khoi", "noi dung " + id, 1_000L, null);
    }
}
