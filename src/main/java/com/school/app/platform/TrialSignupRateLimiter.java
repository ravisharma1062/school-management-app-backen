package com.school.app.platform;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP token bucket for the self-service trial endpoint. Stricter than
 * {@link PublicSignupRateLimiter} — unlike a signup *request*, this provisions a real tenant
 * immediately with no operator review in between, so abuse is more consequential.
 */
@Component
public class TrialSignupRateLimiter {

    private static final int CAPACITY = 3;
    private static final Duration REFILL_PERIOD = Duration.ofHours(1);

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp) {
        return buckets.computeIfAbsent(clientIp, ip -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(CAPACITY, io.github.bucket4j.Refill.intervally(CAPACITY, REFILL_PERIOD));
        return Bucket.builder().addLimit(limit).build();
    }
}
