package com.julio.lifeorganizer.auth.web.dto;

import java.time.Instant;

public record DeleteAccountResponse(Instant deletionScheduledAt) {
}
