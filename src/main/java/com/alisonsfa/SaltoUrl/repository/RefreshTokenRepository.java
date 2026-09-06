package com.alisonsfa.SaltoUrl.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;
import com.alisonsfa.SaltoUrl.domain.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>{
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
