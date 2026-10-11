package com.interviewiq.web.dto;

import java.time.Instant;

/** Login/registration response. The token is held by the client; the hash never leaves the server. */
public record AuthResponse(
        String token,
        Instant expiresAt,
        UserResponse user,
        long ttlSeconds
) {
}
