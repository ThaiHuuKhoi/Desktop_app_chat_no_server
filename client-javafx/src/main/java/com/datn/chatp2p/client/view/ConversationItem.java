package com.datn.chatp2p.client.view;

/**
 * 1 dong trong danh sach hoi thoai o thanh ben: khung nhom ({@code peerId == null}) hoac tin rieng
 * voi 1 nguoi. Moi dong ung voi 1 tab (an) trong {@code chatTabs}.
 *
 * @param subtitle dong phu (vd "3 người" cho nhom), co the rong.
 * @param unread   so tin nguoi khac gui ma minh chua xem.
 */
record ConversationItem(String peerId, String title, String subtitle, int unread) {

    boolean isGroup() {
        return peerId == null;
    }
}
