package com.alisonsfa.SaltoUrl.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alisonsfa.SaltoUrl.domain.entity.ClickEvent;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID>{
    long countByLinkId(UUID linkId);

    List<ClickEvent> findTop10ByLinkIdOrderByClickedAtDesc(UUID linkId);
}
