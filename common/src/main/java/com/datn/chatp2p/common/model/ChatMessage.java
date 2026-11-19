package com.datn.chatp2p.common.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Mot tin nhan van ban trong phong chat.
 *
 * <p>Phong theo {@code UnsentMessage} / {@code ReceivedMessage} trong
 * {@code models/chat.ts} cua chitchatter: {@code timeReceived} la {@code null}
 * cho toi khi tin duoc xac nhan da toi (hoac la tin cua chinh minh vua gui).
 */
public final class ChatMessage {

    private final String id;
    private final String authorId;
    private final String text;
    private final long timeSent;
    private final String replyToId;
    private Long timeReceived;

    public ChatMessage(String authorId, String text) {
        this(UUID.randomUUID().toString(), authorId, text, System.currentTimeMillis(), null);
    }

    public ChatMessage(String id, String authorId, String text, long timeSent, Long timeReceived) {
        this(id, authorId, text, timeSent, timeReceived, null);
    }

    /** @param replyToId id cua tin dang duoc tra loi, hoac null. */
    public ChatMessage(String id, String authorId, String text, long timeSent, Long timeReceived, String replyToId) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.text = Objects.requireNonNull(text, "text");
        this.timeSent = timeSent;
        this.timeReceived = timeReceived;
        this.replyToId = replyToId;
    }

    /** Tin moi cua minh tra loi tin {@code replyToId}. */
    public static ChatMessage reply(String authorId, String text, String replyToId) {
        return new ChatMessage(UUID.randomUUID().toString(), authorId, text, System.currentTimeMillis(), null, replyToId);
    }

    /** Id cua tin dang duoc tra loi, hoac null neu la tin thuong. */
    public String getReplyToId() {
        return replyToId;
    }

    public String getId() {
        return id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public Long getTimeReceived() {
        return timeReceived;
    }

    public boolean isReceived() {
        return timeReceived != null;
    }

    public void markReceived() {
        this.timeReceived = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "ChatMessage{id=%s, authorId=%s, text=%s, timeSent=%d, timeReceived=%s}"
                .formatted(id, authorId, text, timeSent, timeReceived);
    }
}
