package com.rto.security;

import com.rto.core.ApiException;
import com.rto.core.Settings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HS256 access/refresh tokens. The schema has no session table, so revoked token ids (logout, refresh rotation) are
 * kept in memory until they expire - back this with Redis for multi-instance deployments.
 */
@Component
public class JwtService {
    private final Settings settings;
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();
    private volatile SecretKey key;

    public JwtService(Settings settings) {
        this.settings = settings;
    }

    private SecretKey key() {
        SecretKey k = key;
        if (k == null) {
            byte[] raw = settings.jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (raw.length < 32) throw new IllegalStateException("JWT_SECRET must be set (>= 32 chars) in backend-java/.env");
            key = k = Keys.hmacShaKeyFor(raw);
        }
        return k;
    }

    private String build(long userId, String type, Duration life) {
        Instant now = Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("typ", type).id(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(life))).signWith(key()).compact();
    }

    public String accessToken(long userId) {
        return build(userId, "access", Duration.ofMinutes(settings.accessTokenMinutes()));
    }

    public String refreshToken(long userId) {
        return build(userId, "refresh", Duration.ofDays(settings.refreshTokenDays()));
    }

    /** Rejects non-canonical tokens (e.g. junk appended to the signature, which lenient decoders silently ignore). */
    private static void requireCanonical(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw ApiException.unauthorized("INVALID_TOKEN", "Invalid token");
        try {
            for (String p : parts) {
                byte[] raw = java.util.Base64.getUrlDecoder().decode(p);
                if (!java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(p)) {
                    throw new IllegalArgumentException("non-canonical");
                }
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid token");
        }
    }

    public Claims decode(String token, String expectedType) {
        requireCanonical(token);
        Claims c;
        try {
            c = Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "Token has expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid token");
        }
        if (!expectedType.equals(c.get("typ", String.class)) || isRevoked(c.getId())) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid token");
        }
        return c;
    }

    public void revoke(Claims claims) {
        long now = System.currentTimeMillis();
        revoked.values().removeIf(exp -> exp < now);
        revoked.put(claims.getId(), claims.getExpiration().getTime());
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.containsKey(jti);
    }
}
