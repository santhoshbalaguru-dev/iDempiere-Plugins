package com.example.idempiere.assistant.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Binds each conversation id to the token fingerprint that created it, so one user
 * cannot read or continue another user's conversation. In-memory for Phase 1.
 */
@Component
public class ConversationRegistry {

    private static final int MAX_CONVERSATIONS = 10_000;

    private final Map<String, String> owners = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CONVERSATIONS;
        }
    };

    public synchronized String resolve(String requestedId, String ownerFingerprint) {
        if (requestedId == null || requestedId.isBlank()) {
            String id = UUID.randomUUID().toString();
            owners.put(id, ownerFingerprint);
            return id;
        }
        UUID.fromString(requestedId); // rejects anything that is not a UUID
        String owner = owners.putIfAbsent(requestedId, ownerFingerprint);
        if (owner != null && !owner.equals(ownerFingerprint)) {
            throw new ConversationAccessException();
        }
        return requestedId;
    }

    public static class ConversationAccessException extends RuntimeException {
        public ConversationAccessException() {
            super("Conversation belongs to another session");
        }
    }
}
