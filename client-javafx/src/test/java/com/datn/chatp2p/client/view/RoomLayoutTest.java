package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomLayoutTest {

    @Test
    void modeFollowsWindowWidth() {
        assertEquals(RoomLayout.Mode.WIDE, RoomLayout.modeFor(1240));
        assertEquals(RoomLayout.Mode.WIDE, RoomLayout.modeFor(RoomLayout.WIDE_MIN));
        assertEquals(RoomLayout.Mode.MEDIUM, RoomLayout.modeFor(1000));
        assertEquals(RoomLayout.Mode.MEDIUM, RoomLayout.modeFor(RoomLayout.MEDIUM_MIN));
        assertEquals(RoomLayout.Mode.COMPACT, RoomLayout.modeFor(899));
        assertEquals(RoomLayout.Mode.COMPACT, RoomLayout.modeFor(560));
    }

    @Test
    void wideShowsAllThreeColumns() {
        RoomLayout layout = new RoomLayout(1240);
        assertTrue(layout.sidebarVisible());
        assertTrue(layout.membersVisible());
        assertFalse(layout.sidebarToggleVisible());
        assertTrue(layout.statusChipsVisible());
    }

    @Test
    void mediumHidesMembersButCanOpenThem() {
        RoomLayout layout = new RoomLayout(1000);
        assertTrue(layout.sidebarVisible());
        assertFalse(layout.membersVisible());
        layout.toggleMembers();
        assertTrue(layout.membersVisible());
        assertTrue(layout.sidebarVisible(), "Du cho cho ca 2 cot ben o MEDIUM");
        layout.toggleSidebar();
        assertTrue(layout.sidebarVisible(), "Cot hoi thoai khong an duoc o MEDIUM");
    }

    @Test
    void compactKeepsOnlyChatAndOpensOneSidePanelAtATime() {
        RoomLayout layout = new RoomLayout(600);
        assertFalse(layout.sidebarVisible());
        assertFalse(layout.membersVisible());
        assertTrue(layout.sidebarToggleVisible());
        assertFalse(layout.statusChipsVisible());

        layout.toggleSidebar();
        assertTrue(layout.sidebarVisible());
        layout.toggleMembers();
        assertTrue(layout.membersVisible());
        assertFalse(layout.sidebarVisible(), "Mo cot phai thi dong cot trai");
        layout.toggleSidebar();
        assertTrue(layout.sidebarVisible());
        assertFalse(layout.membersVisible(), "Mo cot trai thi dong cot phai");
    }

    @Test
    void crossingABreakpointResetsToThatModesDefaults() {
        RoomLayout layout = new RoomLayout(1240);
        layout.toggleMembers();
        assertFalse(layout.membersVisible());

        assertFalse(layout.resize(1200), "Cung che do -> giu lua chon cua nguoi dung");
        assertFalse(layout.membersVisible());

        assertTrue(layout.resize(700));
        assertFalse(layout.sidebarVisible());
        assertTrue(layout.resize(1300));
        assertTrue(layout.sidebarVisible());
        assertTrue(layout.membersVisible());
    }
}
