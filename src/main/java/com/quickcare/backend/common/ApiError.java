package com.quickcare.backend.common;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String message
) {
}
