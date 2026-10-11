package com.interviewiq.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory brute-force protection for the auth endpoints.
 *
 * Two independent budgets per key:
 *   - failures per email+IP  → short lockout after N bad passwords
 *   - attempts per IP        → hourly ceiling, blocks broad spraying
 *
 * Deliberately dependency-free; swap for Bucket4j/Redis if the API is ever
 * scaled horizontally (the same interface keeps the call sites unchanged).
 */
@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_FAILURES_PER_KEY = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS_PER_IP = 30;
    private static final Duration IP_WINDOW = Duration.ofHours(1);

    private record Counter(AtomicInteger value, Instant expiresAt) {
    }

    private final Map<String, Counter> failures = new ConcurrentHashMap<>();
    private final Map<String, Counter> perIp = new ConcurrentHashMap<>();

    public record Status(boolean allowed, long retryAfterSeconds, String reason) {

        static Status ok() {
            return new Status(true, 0, null);
        }
    }

    /** Call before checking credentials. */
    public Status checkAllowed(String email, String clientIp) {
        String key = keyOf(email, clientIp);
        Counter locked = failures.get(key);
        if (locked != null && locked.value().get() >= MAX_FAILURES_PER_KEY && Instant.now().isBefore(locked.expiresAt())) {
            long retry = Math.max(1, Duration.between(Instant.now(), locked.expiresAt()).toSeconds());
            log.warn("Login blocked for {} ({}) — lockout {}s remaining", mask(email), clientIp, retry);
            return new Status(false, retry, "Too many failed attempts. Try again in " + (retry / 60 + 1) + " minute(s).");
        }
        Counter ipCounter = perIp.compute(keyOf("ip", clientIp), (k, existing) -> {
            if (existing == null || Instant.now().isAfter(existing.expiresAt())) {
                return new Counter(new AtomicInteger(1), Instant.now().plus(IP_WINDOW));
            }
            existing.value().incrementAndGet();
            return existing;
        });
        if (ipCounter.value().get() > MAX_ATTEMPTS_PER_IP) {
            long retry = Math.max(1, Duration.between(Instant.now(), ipCounter.expiresAt()).toSeconds());
            log.warn("Rate limit hit for IP {} — {} attempts", clientIp, ipCounter.value().get());
            return new Status(false, retry, "Too many attempts from this address. Try again later.");
        }
        return Status.ok();
    }

    public void recordFailure(String email, String clientIp) {
        String key = keyOf(email, clientIp);
        failures.compute(key, (k, existing) -> {
            if (existing == null || Instant.now().isAfter(existing.expiresAt())) {
                return new Counter(new AtomicInteger(1), Instant.now().plus(LOCKOUT));
            }
            existing.value().incrementAndGet();
            return existing;
        });
    }

    public void recordSuccess(String email, String clientIp) {
        failures.remove(keyOf(email, clientIp));
    }

    /** How many more bad passwords this key can take before lockout. */
    public int remainingAttempts(String email, String clientIp) {
        Counter counter = failures.get(keyOf(email, clientIp));
        if (counter == null || Instant.now().isAfter(counter.expiresAt())) {
            return MAX_FAILURES_PER_KEY;
        }
        return Math.max(0, MAX_FAILURES_PER_KEY - counter.value().get());
    }

    @Scheduled(fixedDelay = 600_000)
    void evictExpired() {
        Instant now = Instant.now();
        failures.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
        perIp.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private static String keyOf(String email, String ip) {
        return (email == null ? "-" : email.trim().toLowerCase()) + "|" + (ip == null ? "-" : ip);
    }

    /** Never log full email addresses from a security path. */
    private static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "unknown";
        }
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
