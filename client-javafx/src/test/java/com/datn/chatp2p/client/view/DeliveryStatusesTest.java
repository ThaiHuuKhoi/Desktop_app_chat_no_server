package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryStatusesTest {

    @Test
    void groupMessageGoesFromSendingToPartialToDelivered() {
        DeliveryStatuses statuses = new DeliveryStatuses();
        statuses.sent("m1", List.of("a", "b"));
        assertEquals("Đang gửi", statuses.status("m1").label());

        assertTrue(statuses.delivered("m1", "a"));
        assertEquals("Đã nhận 1/2", statuses.status("m1").label());

        assertTrue(statuses.delivered("m1", "b"));
        assertEquals("Tất cả đã nhận", statuses.status("m1").label());
        assertFalse(statuses.delivered("m1", "b"), "ACK trung khong doi trang thai");
    }

    @Test
    void directMessageAndFailures() {
        DeliveryStatuses statuses = new DeliveryStatuses();
        statuses.sent("dm", List.of("a"));
        assertTrue(statuses.failed("dm", "a"));
        assertEquals(DeliveryStatuses.Status.Kind.FAILED, statuses.status("dm").kind());

        // ACK toi muon van tinh la da nhan
        assertTrue(statuses.delivered("dm", "a"));
        assertEquals("Đã nhận", statuses.status("dm").label());
    }

    @Test
    void peerLeavingBeforeAckIsNotStuckSending() {
        DeliveryStatuses statuses = new DeliveryStatuses();
        statuses.sent("m1", List.of("a", "b"));
        statuses.delivered("m1", "a");

        assertTrue(statuses.peerLeft("b"));
        assertEquals(DeliveryStatuses.Status.Kind.PARTIAL, statuses.status("m1").kind());
    }

    @Test
    void emptyRoomAndUnknownMessages() {
        DeliveryStatuses statuses = new DeliveryStatuses();
        statuses.sent("alone", List.of());
        assertEquals(DeliveryStatuses.Status.Kind.NO_RECIPIENT, statuses.status("alone").kind());
        assertEquals("", statuses.status("alone").label());
        assertNull(statuses.status("from-history"));
        assertFalse(statuses.delivered("m1", "stranger"), "ACK cua nguoi khong phai nguoi nhan bi bo qua");
    }
}
