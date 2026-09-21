package com.alisonsfa.SaltoUrl.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.alisonsfa.SaltoUrl.domain.entity.Link;

public interface LinkRepository extends JpaRepository<Link, UUID>{
    Optional<Link> findByCodeAndActiveTrue(String code);
    Optional<Link> findByCode(String code);

    Optional<Link> findByCodeAndUserId(String code, UUID userId);

    Page<Link> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Modifying 
    @Query ("""
            UPDATE Link l
            SET l.active = false
            WHERE l.active = true AND l.expiesAt IS NOT NULL AND l.expiresAt < :now
            """)
    int deactivateExpiredLinks(@Param("now") LocalDateTime now);
}
