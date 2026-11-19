package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import com.datn.chatp2p.signaling.room.RoomRegistry.RoomSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoomRegistryActiveRoomsTest {

    @Test
    void passwordRoomsAreListedWithFlagButWithoutTheirTag() {
        RoomRegistry registry = new RoomRegistry();
        registry.join(new PeerSession(new FakeWebSocketSession("s1"), "phong-cong-khai", "p1", "An"));
        registry.join(new PeerSession(new FakeWebSocketSession("s2"), "phong-rieng~3f9c0a1b2c3d4e5f60718293", "p2", "Binh"));
        registry.join(new PeerSession(new FakeWebSocketSession("s3"), "phong-rieng~3f9c0a1b2c3d4e5f60718293", "p3", "Chi"));

        List<RoomSnapshot> rooms = registry.activeRooms().stream()
                .sorted(Comparator.comparing(RoomSnapshot::roomId)).toList();

        assertEquals(List.of(
                new RoomSnapshot("phong-cong-khai", 1, "An", false),
                new RoomSnapshot("phong-rieng", 2, rooms.get(1).ownerUserName(), true)), rooms);
        assertFalse(rooms.get(1).roomId().contains("~"), "The suy ra tu mat khau khong duoc lo ra ngoai");
    }
}
