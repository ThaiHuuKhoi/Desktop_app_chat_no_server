package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.model.ChatMessage;

import java.util.Map;

/** Nhung gi 1 o tin nhan can ngoai ban than tin: trang thai giao, cam xuc, tin duoc tra loi, thao tac. */
interface MessageContext {

    /** null neu khong phai tin minh gui trong phien nay. */
    DeliveryStatuses.Status delivery(String messageId);

    /** Cam xuc -&gt; so nguoi (rong neu chua co). */
    Map<String, Integer> reactions(String messageId);

    boolean reactedBySelf(String messageId, String emoji);

    /** Tim tin theo id (de hien trich dan khi tra loi), null neu khong con. */
    ChatMessage findMessage(String messageId);

    void reply(ChatMessage message);

    /** Bat / tat cam xuc {@code emoji} cua minh tren tin. */
    void toggleReaction(ChatMessage message, String emoji);

    /** Tin da bi tac gia sua. */
    default boolean isEdited(String messageId) {
        return false;
    }

    /** Tin da bi tac gia xoa. */
    default boolean isDeleted(String messageId) {
        return false;
    }

    /** Sua tin cua minh. */
    default void edit(ChatMessage message) {
    }

    /** Xoa tin cua minh. */
    default void delete(ChatMessage message) {
    }

    /** Dung cho test / noi khong can tuong tac. */
    MessageContext NONE = new MessageContext() {
        @Override
        public DeliveryStatuses.Status delivery(String messageId) {
            return null;
        }

        @Override
        public Map<String, Integer> reactions(String messageId) {
            return Map.of();
        }

        @Override
        public boolean reactedBySelf(String messageId, String emoji) {
            return false;
        }

        @Override
        public ChatMessage findMessage(String messageId) {
            return null;
        }

        @Override
        public void reply(ChatMessage message) {
        }

        @Override
        public void toggleReaction(ChatMessage message, String emoji) {
        }
    };
}
