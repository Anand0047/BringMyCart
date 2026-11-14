package com.bringmycart.bmc.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bringmycart.bmc.identity.exception.ApiException;
import com.bringmycart.bmc.identity.security.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationRateLimiter {
    private static final int MAX_TRACKED_KEYS = 10_000;
    private final AuthProperties properties;
    private final Map<String, AttemptWindow> attempts = new LinkedHashMap<>(16, 0.75F, true);

    public AuthenticationRateLimiter(AuthProperties properties) {
        this.properties = properties;
    }

    public synchronized void check(String operation, String email) {
        String key = operation + ":" + email;
        AttemptWindow window = attempts.get(key);
        if (window != null && window.isCurrent()) {
            if (window.count >= properties.getRateLimitMaxAttempts()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Too many attempts. Try again later.");
            }
            return;
        }
        attempts.remove(key);
    }

    public synchronized void recordFailure(String operation, String email) {
        String key = operation + ":" + email;
        AttemptWindow window = attempts.get(key);
        if (window == null || !window.isCurrent()) {
            window = new AttemptWindow(Instant.now().plus(Duration.ofMinutes(properties.getRateLimitWindowMinutes())));
            attempts.put(key, window);
        }
        window.count++;
        while (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.remove(attempts.keySet().iterator().next());
        }
    }

    public synchronized void clear(String operation, String email) {
        attempts.remove(operation + ":" + email);
    }

    private static final class AttemptWindow {
        private final Instant expiresAt;
        private int count;
        private AttemptWindow(Instant expiresAt) { this.expiresAt = expiresAt; }
        private boolean isCurrent() { return Instant.now().isBefore(expiresAt); }
    }
}
