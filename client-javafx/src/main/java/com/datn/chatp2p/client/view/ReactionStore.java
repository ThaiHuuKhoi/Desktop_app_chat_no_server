package com.datn.chatp2p.client.view;

import com.datn.chatp2p.common.protocol.ReactionPayload;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Cam xuc tren tung tin: tin -&gt; cam xuc -&gt; nhung ai da tha. Chi dung tren JavaFX thread. */
final class ReactionStore {

    private final Map<String, Map<String, Set<String>>> byMessage = new HashMap<>();

    /** @return {@code true} neu co thay doi. */
    boolean apply(String messageId, String peerId, String emoji, boolean added) {
        if (!ReactionPayload.ALLOWED.contains(emoji)) {
            return false;
        }
        Map<String, Set<String>> reactions = byMessage.computeIfAbsent(messageId, id -> new HashMap<>());
        Set<String> people = reactions.computeIfAbsent(emoji, e -> new LinkedHashSet<>());
        boolean changed = added ? people.add(peerId) : people.remove(peerId);
        if (people.isEmpty()) {
            reactions.remove(emoji);
        }
        return changed;
    }

    boolean hasReacted(String messageId, String peerId, String emoji) {
        Set<String> people = byMessage.getOrDefault(messageId, Map.of()).get(emoji);
        return people != null && people.contains(peerId);
    }

    /** Cam xuc -&gt; so nguoi, theo thu tu co dinh trong {@link ReactionPayload#ALLOWED}. */
    Map<String, Integer> counts(String messageId) {
        Map<String, Set<String>> reactions = byMessage.getOrDefault(messageId, Map.of());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String emoji : ReactionPayload.ALLOWED) {
            Set<String> people = reactions.get(emoji);
            if (people != null && !people.isEmpty()) {
                result.put(emoji, people.size());
            }
        }
        return result;
    }

    /** Nhung ai da tha {@code emoji} tren tin, theo thu tu tha (de hien tooltip). */
    java.util.List<String> who(String messageId, String emoji) {
        Set<String> people = byMessage.getOrDefault(messageId, Map.of()).get(emoji);
        return people == null ? java.util.List.of() : java.util.List.copyOf(people);
    }

    /** Nguoi roi phong: bo cam xuc cua ho. @return {@code true} neu co thay doi. */
    boolean peerLeft(String peerId) {
        boolean changed = false;
        for (Map<String, Set<String>> reactions : byMessage.values()) {
            for (Set<String> people : reactions.values()) {
                changed |= people.remove(peerId);
            }
            reactions.values().removeIf(Set::isEmpty);
        }
        return changed;
    }
}
