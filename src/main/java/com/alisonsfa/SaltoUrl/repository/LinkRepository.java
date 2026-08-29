package com.alisonsfa.SaltoUrl.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.Link;

public interface LinkRepository extends JpaRepository<Link, UUID>{
    Optional<Link> findByCodeAndActiveTrue(String code);
}
