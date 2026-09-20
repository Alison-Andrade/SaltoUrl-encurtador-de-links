package com.alisonsfa.SaltoUrl.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LinkStatsResponse(
    String code,
    String originalUrl,
    String shortUrl,
    boolean active,
    long totalClicks,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    List<RecentClickDto> recentClicks
) {
    public record RecentClickDto(
        LocalDateTime clickedAt,
        String userAgent,
        String country
    ) {}
}
