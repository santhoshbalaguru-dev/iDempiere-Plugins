package com.example.idempiere.assistant.security;

import com.example.idempiere.assistant.config.AssistantProperties;
import com.example.idempiere.assistant.idempiere.IdempiereClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Confirms a bearer token with iDempiere before the assistant spends LLM tokens on it.
 * Successful checks are cached briefly, keyed by a hash of the token (never the token itself).
 */
@Component
public class TokenVerifier {

    private static final int MAX_CACHE = 10_000;

    private final IdempiereClient idempiere;
    private final Duration ttl;
    private final Map<String, Instant> verifiedUntil = new ConcurrentHashMap<>();

    public TokenVerifier(IdempiereClient idempiere, AssistantProperties props) {
        this.idempiere = idempiere;
        this.ttl = props.idempiere().verifyTtl();
    }

    public boolean isValid(String token) {
        String key = fingerprint(token);
        Instant until = verifiedUntil.get(key);
        if (until != null && until.isAfter(Instant.now())) {
            return true;
        }
        boolean ok = idempiere.verify(token);
        if (ok) {
            if (verifiedUntil.size() >= MAX_CACHE) {
                verifiedUntil.clear();
            }
            verifiedUntil.put(key, Instant.now().plus(ttl));
        } else {
            verifiedUntil.remove(key);
        }
        return ok;
    }

    public static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
