package com.alisonsfa.SaltoUrl.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.alisonsfa.SaltoUrl.domain.entity.RefreshToken;
import com.alisonsfa.SaltoUrl.domain.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>{
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken r
            WHERE r.expiresAt < :now OR r.revoked = true
            """)
    int deleteExpiredOrRevokedTokens(@Param("now") LocalDateTime now);
}
