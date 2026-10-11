package com.interviewiq.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank String password,
        @NotBlank String confirmPassword,
        /** RECRUITER or STUDENT — anything else is coerced server-side. */
        String role
) {
}
