package com.alisonsfa.SaltoUrl.config.security;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

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


}
