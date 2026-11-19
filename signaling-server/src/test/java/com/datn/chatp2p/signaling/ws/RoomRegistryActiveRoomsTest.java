package com.datn.chatp2p.signaling.ws;

import com.datn.chatp2p.signaling.room.PeerSession;
import com.datn.chatp2p.signaling.room.RoomRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomRegistryActiveRoomsTest {

    @Test
    void passwordProtectedRoomsAreNotListed() {
        RoomRegistry registry = new RoomRegistry();
        registry.join(new PeerSession(new FakeWebSocketSession("s1"), "phong-cong-khai", "p1", "An"));
        registry.join(new PeerSession(new FakeWebSocketSession("s2"), "phong-rieng~3f9c0a1b2c3d4e5f60718293", "p2", "Binh"));

        List<String> listed = registry.activeRooms().stream().map(RoomRegistry.RoomSnapshot::roomId).toList();

        assertEquals(List.of("phong-cong-khai"), listed);
    }
}
