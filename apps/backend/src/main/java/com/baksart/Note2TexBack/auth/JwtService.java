package com.baksart.Note2TexBack.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private final Key signingKey;
    private final long accessTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.accessTtlSec:3600}") long accessTtl
    ) {
        this.signingKey = createSigningKey(secret);
        this.accessTtl = accessTtl;
    }

    public String generateAccessToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(accessTtl)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long parseUserId(String token) {
        var claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build().parseClaimsJws(token).getBody();
        return Long.valueOf(claims.getSubject());
    }

    private Key createSigningKey(String secret) {
        byte[] keyBytes = decodeSecret(secret);
        if (keyBytes.length < 32) {
            keyBytes = hashSecret(keyBytes);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] hashSecret(byte[] secretBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secretBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
