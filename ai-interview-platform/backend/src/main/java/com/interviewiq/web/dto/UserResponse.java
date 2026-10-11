package com.interviewiq.web.dto;

import com.interviewiq.domain.AppUser;
import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name(),
                user.getLastLoginAt(), user.getCreatedAt());
    }
}
