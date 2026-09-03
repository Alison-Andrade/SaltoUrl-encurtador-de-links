package com.alisonsfa.SaltoUrl.config.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service 
public class JwtService {
    
    @Value("${jwt.secret.key}")
    private String secretKey;
    
    private static final long EXPIRATION_TIME = 86400000;

    private Key getSingnInKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(UUID userId, String email) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSingnInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    @PostConstruct 
    public void validateSecretKey() {
        if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET invalid or insecure! The key must be at least 32 bytes.");
        }
    }


}
