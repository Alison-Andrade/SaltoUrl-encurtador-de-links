package com.alisonsfa.SaltoUrl.messaging;

import java.util.UUID;

public record ClickEventPayload(
    UUID linkId,
    String ipHash,
    String userAgent,
    String country
) {}
