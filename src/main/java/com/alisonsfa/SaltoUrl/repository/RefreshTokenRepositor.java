package com.alisonsfa.SaltoUrl.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;

public interface RefreshTokenRepositor extends JpaRepository<RefreshToken, UUID>{
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
