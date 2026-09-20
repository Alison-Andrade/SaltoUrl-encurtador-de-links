package com.alisonsfa.SaltoUrl.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.Link;

public interface LinkRepository extends JpaRepository<Link, UUID>{
    Optional<Link> findByCodeAndActiveTrue(String code);
    Optional<Link> findByCode(String code);

    Optional<Link> findByCodeAndUserId(String code, UUID userId);

    Page<Link> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
