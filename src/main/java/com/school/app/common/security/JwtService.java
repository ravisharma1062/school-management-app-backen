package com.school.app.common.security;

import com.school.app.platform.PlatformRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UserDetails userDetails, UUID schoolId) {
        return buildToken(userDetails, schoolId, accessTokenExpirationMs, "access");
    }

    public String generateRefreshToken(UserDetails userDetails, UUID schoolId) {
        return buildToken(userDetails, schoolId, refreshTokenExpirationMs, "refresh");
    }

    private String buildToken(UserDetails userDetails, UUID schoolId, long expirationMs, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", tokenType)
                .claim("scope", "TENANT")
                .claim("school_id", schoolId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Platform tokens carry no {@code school_id} at all (Architecture Decision #3 — platform
     * operators are a separate surface, not scoped to any tenant) and a {@code scope = PLATFORM}
     * claim {@link JwtAuthFilter} uses to keep the two token families non-interchangeable.
     */
    public String generatePlatformAccessToken(String email, PlatformRole role) {
        return buildPlatformToken(email, role, accessTokenExpirationMs, "access");
    }

    public String generatePlatformRefreshToken(String email, PlatformRole role) {
        return buildPlatformToken(email, role, refreshTokenExpirationMs, "refresh");
    }

    private String buildPlatformToken(String email, PlatformRole role, long expirationMs, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("type", tokenType)
                .claim("scope", "PLATFORM")
                .claim("platform_role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Returns {@code null} for a token minted before this claim existed, rather than throwing. */
    public UUID extractSchoolId(String token) {
        String raw = extractClaim(token, claims -> claims.get("school_id", String.class));
        return raw != null ? UUID.fromString(raw) : null;
    }

    /** Tokens minted before this claim existed have no {@code scope} claim and are treated as TENANT. */
    public boolean isPlatformToken(String token) {
        String scope = extractClaim(token, claims -> claims.get("scope", String.class));
        return "PLATFORM".equals(scope);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get("type", String.class));
            return "refresh".equals(type);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
