package com.datn.chatp2p.client.view;

/**
 * Quyet dinh cot nao cua man hinh phong duoc hien theo chieu rong cua so - khung chat o giua la
 * thu quan trong nhat nen luon duoc giu cho, 2 cot ben nhuong cho truoc (kieu Discord / Messenger
 * ban desktop). Logic thuan, khong dung JavaFX, de kiem thu; {@link RoomController} chi ap ket qua.
 *
 * <ul>
 *   <li>{@link Mode#WIDE} (tu {@value #WIDE_MIN} px): du 3 cot.</li>
 *   <li>{@link Mode#MEDIUM}: cot hoi thoai + khung chat; cot nguoi trong phong an, bam nut de mo.</li>
 *   <li>{@link Mode#COMPACT} (duoi {@value #MEDIUM_MIN} px): chi khung chat; moi luc mo toi da 1 cot ben
 *       de khung chat khong bi ep qua hep.</li>
 * </ul>
 *
 * Nguong tinh theo be rong mac dinh cua cot (hoi thoai 260 + nguoi trong phong 270 = 530): o WIDE
 * khung chat con it nhat ~570 px, o MEDIUM mo them cot phai van con ~370 px.
 * Khi cua so vuot qua 1 nguong, trang thai ve mac dinh cua che do moi (nguoi dung bat/tat tay chi
 * co hieu luc trong che do hien tai) - de doan truoc, khong bi "ket" cot da tat tu luc cua so to.
 */
final class RoomLayout {

    static final double WIDE_MIN = 1100;
    static final double MEDIUM_MIN = 900;

    enum Mode { WIDE, MEDIUM, COMPACT }

    private Mode mode;
    private boolean sidebarOpen;
    private boolean membersOpen;

    RoomLayout(double width) {
        applyDefaults(modeFor(width));
    }

    static Mode modeFor(double width) {
        if (width >= WIDE_MIN) {
            return Mode.WIDE;
        }
        return width >= MEDIUM_MIN ? Mode.MEDIUM : Mode.COMPACT;
    }

    /** @return true neu doi che do (can ap lai bo cuc). */
    boolean resize(double width) {
        Mode next = modeFor(width);
        if (next == mode) {
            return false;
        }
        applyDefaults(next);
        return true;
    }

    /** Cot hoi thoai chi an duoc o COMPACT - o che do khac no luon hien (nut bat/tat cung an). */
    void toggleSidebar() {
        if (mode != Mode.COMPACT) {
            return;
        }
        sidebarOpen = !sidebarOpen;
        if (sidebarOpen) {
            membersOpen = false;
        }
    }

    void toggleMembers() {
        membersOpen = !membersOpen;
        if (membersOpen && mode == Mode.COMPACT) {
            sidebarOpen = false;
        }
    }

    Mode mode() {
        return mode;
    }

    boolean sidebarVisible() {
        return sidebarOpen;
    }

    boolean membersVisible() {
        return membersOpen;
    }

    boolean sidebarToggleVisible() {
        return mode == Mode.COMPACT;
    }

    /** Hang chip trang thai tren header chiem cho - o COMPACT nhuong cho ten hoi thoai. */
    boolean statusChipsVisible() {
        return mode != Mode.COMPACT;
    }

    private void applyDefaults(Mode next) {
        mode = next;
        sidebarOpen = next != Mode.COMPACT;
        membersOpen = next == Mode.WIDE;
    }
}
