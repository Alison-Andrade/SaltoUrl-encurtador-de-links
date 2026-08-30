package com.alisonsfa.SaltoUrl.dto;

import java.time.LocalDateTime;

public record LinkResponse(
    String code,
    String originalUrl,
    String shortUrl,
    LocalDateTime createdAt
) {}
