package com.school.app.platform;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-IP token bucket for the public signup endpoint (MT-4's only new unauthenticated surface).
 * In-memory and per-instance — fine for this app's current single-instance Render deployment;
 * a multi-instance deployment would need a shared store (e.g. Redis) instead. The bucket map is
 * never evicted, so long-lived deployments will slowly accumulate one entry per distinct caller
 * IP — acceptable for now, worth revisiting if this ever becomes a real memory concern.
 */
@Component
public class PublicSignupRateLimiter {

    private static final int CAPACITY = 5;
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
