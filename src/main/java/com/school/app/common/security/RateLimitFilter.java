package com.school.app.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.app.common.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * General-purpose per-caller rate limiting for the authenticated API, on top of (not replacing)
 * the tighter, endpoint-specific limiters already in place for the unauthenticated public signup
 * endpoints ({@code PublicSignupRateLimiter}, {@code TrialSignupRateLimiter}) — this filter
 * deliberately skips {@code /api/v1/public/**}, since those already have their own tuned limits.
 * <p>
 * Keyed by the authenticated principal's username when available (set by {@link JwtAuthFilter},
 * which must run first — see the filter ordering in {@code SecurityConfig}), falling back to the
 * client IP for anything that reaches this filter unauthenticated. In-memory and per-instance,
 * same caveat as the existing public-endpoint limiters: fine for this app's current single-instance
 * Render deployment, would need a shared store (e.g. Redis) for a multi-instance one. The bucket
 * map is never evicted, so long-lived deployments slowly accumulate one entry per distinct caller
 * — acceptable for now, worth revisiting if this ever becomes a real memory concern.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.requests-per-minute:300}")
    private int requestsPerMinute;

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/public/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = callerKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        if (!bucket.tryConsume(1)) {
            writeTooManyRequests(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests",
                "Rate limit exceeded. Please slow down and try again shortly.", request.getRequestURI(), null, "RATE_LIMITED");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
