package com.alisonsfa.SaltoUrl.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.ClickEvent;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID>{
    long countByLinkId(UUID linkId);
}
